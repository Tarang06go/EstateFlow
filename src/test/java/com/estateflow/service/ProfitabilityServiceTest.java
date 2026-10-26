package com.estateflow.service;

import com.estateflow.dao.ProfitabilityDao;
import com.estateflow.dao.ProfitabilityDao.*;
import com.estateflow.dto.ProfitabilityReport;
import com.estateflow.dto.ProfitabilityReport.PropertyProfit;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.exception.ValidationException;
import com.estateflow.model.enums.ExpenseCategory;
import com.estateflow.model.enums.Role;
import com.estateflow.report.CsvWriter;
import com.estateflow.report.ReportFormat;
import com.estateflow.security.Authorizer;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Aggregation, NOI arithmetic, ordering, zero-filling and validation. SQL date filtering is covered by the integration test. */
class ProfitabilityServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);

    /** Returns canned rows and records the arguments it was called with. */
    static class StubDao implements ProfitabilityDao {
        List<PropertyRef> props = new ArrayList<>();
        List<IncomeRow> income = new ArrayList<>();
        List<ExpenseAggRow> expenses = new ArrayList<>();
        List<ChargedRow> charged = new ArrayList<>();
        LocalDate lastFrom, lastTo;
        Long lastProperty;
        int calls;

        public List<PropertyRef> properties(Long id) { calls++; lastProperty = id; return props; }
        public List<IncomeRow> receivedRent(LocalDate f, LocalDate t, Long id) { lastFrom = f; lastTo = t; return income; }
        public List<ExpenseAggRow> expenses(LocalDate f, LocalDate t, Long id) { return expenses; }
        public List<ChargedRow> chargedRent(LocalDate f, LocalDate t, Long id) { return charged; }
    }

    private StubDao dao;

    private ProfitabilityService serviceFor(Authorizer auth) {
        return new ProfitabilityService(dao, new CsvWriter(), new PassThroughTransactionManager(), CLOCK, auth);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @BeforeEach
    void setUp() {
        dao = new StubDao();
        dao.props.add(new PropertyRef(1, "Alpha", true));
        dao.props.add(new PropertyRef(2, "Beta", true));
        dao.props.add(new PropertyRef(3, "Idle", true));
        dao.props.add(new PropertyRef(4, "Old inactive", false));
        dao.props.add(new PropertyRef(5, "Sold but had costs", false));
    }

    @Test
    void totalsAndNoiAreRentReceivedMinusOperatingExpenses() throws Exception {
        dao.income.add(new IncomeRow(1, YearMonth.of(2026, 8), bd("15000.00")));
        dao.income.add(new IncomeRow(1, YearMonth.of(2026, 9), bd("15000.00")));
        dao.income.add(new IncomeRow(2, YearMonth.of(2026, 9), bd("9000.00")));
        dao.expenses.add(new ExpenseAggRow(1, YearMonth.of(2026, 8), ExpenseCategory.UTILITIES, bd("2000.00"), false));
        dao.expenses.add(new ExpenseAggRow(1, YearMonth.of(2026, 9), ExpenseCategory.INSURANCE, bd("3000.50"), false));
        dao.expenses.add(new ExpenseAggRow(2, YearMonth.of(2026, 9), ExpenseCategory.MAINTENANCE, bd("12000.00"), true));
        dao.charged.add(new ChargedRow(1, bd("30000.00")));
        dao.charged.add(new ChargedRow(2, bd("15000.00")));

        ProfitabilityReport r = serviceFor(TestAuth.as(Role.VIEWER))
                .report(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30), null);

        assertEquals(bd("39000.00"), r.rentReceived());
        assertEquals(bd("45000.00"), r.rentCharged(), "charged is reported separately from received");
        assertEquals(bd("17000.50"), r.expenses());
        assertEquals(bd("21999.50"), r.noi());
        assertEquals(bd("12000.00"), r.maintenanceFromRequests());

        // property rows: NOI = received - expenses, best first, sums match the totals
        PropertyProfit alpha = r.properties().stream().filter(p -> p.propertyName().equals("Alpha")).findFirst().orElseThrow();
        assertEquals(bd("30000.00"), alpha.rentReceived());
        assertEquals(bd("5000.50"), alpha.expenses());
        assertEquals(bd("24999.50"), alpha.noi());
        PropertyProfit beta = r.properties().stream().filter(p -> p.propertyName().equals("Beta")).findFirst().orElseThrow();
        assertEquals(bd("-3000.00"), beta.noi(), "a loss is shown as negative NOI");
        assertEquals("Alpha", r.properties().get(0).propertyName());
        assertEquals(r.noi(), r.properties().stream().map(PropertyProfit::noi).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(r.rentReceived(), r.properties().stream().map(PropertyProfit::rentReceived).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void categoryBreakdownIsSortedAndOmitsZeroCategories() throws Exception {
        dao.expenses.add(new ExpenseAggRow(1, YearMonth.of(2026, 9), ExpenseCategory.UTILITIES, bd("100.00"), false));
        dao.expenses.add(new ExpenseAggRow(1, YearMonth.of(2026, 9), ExpenseCategory.UTILITIES, bd("50.00"), false));
        dao.expenses.add(new ExpenseAggRow(2, YearMonth.of(2026, 9), ExpenseCategory.PROPERTY_TAX, bd("900.00"), false));
        ProfitabilityReport r = serviceFor(TestAuth.admin()).report(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);
        assertEquals(2, r.categories().size());
        assertEquals(ExpenseCategory.PROPERTY_TAX, r.categories().get(0).category());
        assertEquals(bd("150.00"), r.categories().get(1).amount());
        assertEquals(r.expenses(), r.categories().stream().map(c -> c.amount()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void monthlyTrendIsZeroFilledAcrossAYearBoundary() throws Exception {
        dao.income.add(new IncomeRow(1, YearMonth.of(2026, 12), bd("100.00")));
        dao.expenses.add(new ExpenseAggRow(1, YearMonth.of(2027, 2), ExpenseCategory.OTHER, bd("40.00"), false));
        ProfitabilityReport r = serviceFor(TestAuth.admin()).report(LocalDate.of(2026, 11, 15), LocalDate.of(2027, 2, 1), null);
        assertEquals(List.of(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2)),
                r.months().stream().map(m -> m.month()).toList());
        assertEquals(bd("0.00"), r.months().get(0).income());
        assertEquals(bd("100.00"), r.months().get(1).income());
        assertEquals(bd("-40.00"), r.months().get(3).noi());
        assertEquals(bd("60.00"), r.months().stream().map(m -> m.noi()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void emptyDataGivesZerosNotErrors() throws Exception {
        ProfitabilityReport r = serviceFor(TestAuth.admin()).report(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null);
        assertTrue(r.isEmpty());
        assertEquals(bd("0.00"), r.noi());
        assertEquals(3, r.months().size());
        assertTrue(r.categories().isEmpty());
        assertEquals(List.of("Alpha", "Beta", "Idle"), r.properties().stream().map(PropertyProfit::propertyName).sorted().toList(),
                "active properties are listed with zeros; inactive ones without activity are not");
    }

    @Test
    void inactivePropertiesAppearOnlyWhenTheyHadActivity() throws Exception {
        dao.expenses.add(new ExpenseAggRow(5, YearMonth.of(2026, 9), ExpenseCategory.OTHER, bd("10.00"), false));
        ProfitabilityReport r = serviceFor(TestAuth.admin()).report(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);
        assertTrue(r.properties().stream().anyMatch(p -> p.propertyName().equals("Sold but had costs") && !p.active()));
        assertTrue(r.properties().stream().noneMatch(p -> p.propertyName().equals("Old inactive")));
    }

    @Test
    void singleDayAndSingleMonthRangesWork() throws Exception {
        LocalDate day = LocalDate.of(2026, 2, 28);
        ProfitabilityReport r = serviceFor(TestAuth.admin()).report(day, day, 1L);
        assertEquals(1, r.months().size());
        assertEquals(day, dao.lastFrom);
        assertEquals(day, dao.lastTo);
        assertEquals(1L, dao.lastProperty, "the property filter is passed through");
    }

    @Test
    void invalidRangesAreRejectedBeforeAnyQuery() {
        ProfitabilityService s = serviceFor(TestAuth.admin());
        assertThrows(ValidationException.class, () -> s.report(null, LocalDate.of(2026, 1, 1), null));
        assertThrows(ValidationException.class, () -> s.report(LocalDate.of(2026, 1, 1), null, null));
        assertThrows(ValidationException.class, () -> s.report(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), null));
        assertThrows(ValidationException.class, () -> s.report(LocalDate.of(2010, 1, 1), LocalDate.of(2026, 1, 1), null), "over 10 years");
        assertEquals(0, dao.calls);
        assertDoesNotThrow(() -> s.report(LocalDate.of(2017, 1, 1), LocalDate.of(2026, 12, 31), null), "exactly 120 months is allowed");
    }

    @Test
    void reportsNeedViewReportsPermissionAndSignedOutIsDenied() {
        assertDoesNotThrow(() -> serviceFor(TestAuth.as(Role.MANAGER)).report(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null));
        assertDoesNotThrow(() -> serviceFor(TestAuth.as(Role.VIEWER)).report(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null));
        dao.calls = 0;
        ProfitabilityService out = serviceFor(TestAuth.signedOut());
        assertThrows(AuthorizationException.class, () -> out.report(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null));
        assertThrows(AuthorizationException.class, () -> out.exportProperties(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null,
                Path.of("never.csv"), ReportFormat.CSV));
        assertEquals(0, dao.calls);
    }

    @Test
    void csvExportListsPropertiesAndATotalRow(@TempDir Path dir) throws Exception {
        dao.income.add(new IncomeRow(1, YearMonth.of(2026, 9), bd("1000.00")));
        dao.expenses.add(new ExpenseAggRow(1, YearMonth.of(2026, 9), ExpenseCategory.OTHER, bd("250.00"), false));
        Path out = dir.resolve("p.csv");
        int rows = serviceFor(TestAuth.admin()).exportProperties(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, out, ReportFormat.CSV);
        assertEquals(3, rows, "Alpha, Beta, Idle");
        String csv = Files.readString(out);
        assertTrue(csv.contains("Property") && csv.contains("NOI"));
        assertTrue(csv.contains("TOTAL"));
        assertTrue(csv.contains("750.00"), "total NOI");
    }
}
