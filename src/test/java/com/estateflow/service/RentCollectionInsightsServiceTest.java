package com.estateflow.service;

import com.estateflow.dao.RentCollectionInsightsDao;
import com.estateflow.dto.RentCollectionInsightsReport;
import com.estateflow.dto.RentCollectionInsightsReport.*;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.enums.Role;
import com.estateflow.report.ReportFormat;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentCollectionInsightsServiceTest {
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temp;
    private final RentCollectionInsightsDao dao = mock(RentCollectionInsightsDao.class);

    private RentCollectionInsightsService service(com.estateflow.security.Authorizer auth) {
        return new RentCollectionInsightsService(dao, new PassThroughTransactionManager(), CLOCK,
                new com.estateflow.report.CsvWriter(), auth);
    }

    @Test void viewerGetsAggregatesButNeverReadsOrExportsTenantLevelData() throws Exception {
        stubReportRows();
        var report = service(TestAuth.as(Role.VIEWER)).report(FROM, TO, null);
        assertTrue(report.tenants().isEmpty());
        assertTrue(report.paymentBehavior().isEmpty());
        verify(dao, never()).byTenant(any(), any(), any());
        verify(dao, never()).paymentBehavior(any(), any(), any(), anyBoolean());
        verify(dao).followUp(LocalDate.of(2026, 10, 26), null, false);

        Path file = temp.resolve("viewer.csv");
        service(TestAuth.as(Role.VIEWER)).export(FROM, TO, null, file, ReportFormat.CSV);
        String csv = Files.readString(file);
        assertFalse(csv.contains("Tenant Secret"));
    }

    @Test void managerGetsTenantDetailsAndTwelveMonthBehaviorWindow() throws Exception {
        stubReportRows();
        var report = service(TestAuth.as(Role.MANAGER)).report(LocalDate.of(2024, 1, 1), TO, 9L);
        assertEquals(LocalDate.of(2025, 11, 1), report.behaviorFrom());
        assertEquals(1, report.tenants().size());
        assertEquals(1, report.paymentBehavior().size());
        verify(dao).byTenant(LocalDate.of(2024, 1, 1), TO, 9L);
        verify(dao).paymentBehavior(LocalDate.of(2025, 11, 1), TO, 9L, true);
    }

    @Test void reportsRejectInvalidDatesOrPropertyBeforeQueries() {
        var service = service(TestAuth.admin());
        assertThrows(com.estateflow.exception.ValidationException.class, () -> service.report(null, TO, null));
        assertThrows(com.estateflow.exception.ValidationException.class, () -> service.report(TO, FROM, null));
        assertThrows(com.estateflow.exception.ValidationException.class, () -> service.report(FROM.minusYears(11), TO, null));
        assertThrows(com.estateflow.exception.ValidationException.class, () -> service.report(FROM, TO, 0L));
        verifyNoInteractions(dao);
    }

    @Test void signedOutCannotReadInsights() {
        assertThrows(AuthorizationException.class, () -> service(TestAuth.signedOut()).report(FROM, TO, null));
        verifyNoInteractions(dao);
    }

    @Test void zeroChargesRemainAZeroRateAndEmptyTablesExportSafely() throws Exception {
        when(dao.totals(FROM, TO, null)).thenReturn(new CollectionTotals(0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(dao.monthlyTrend(FROM, TO, null)).thenReturn(List.of());
        when(dao.byProperty(FROM, TO, null)).thenReturn(List.of());
        when(dao.byTenant(FROM, TO, null)).thenReturn(List.of());
        when(dao.arrears(any(), isNull())).thenReturn(List.of());
        when(dao.followUp(any(), isNull(), anyBoolean())).thenReturn(List.of());
        when(dao.paymentBehavior(any(), any(), isNull(), anyBoolean())).thenReturn(List.of());
        Path file = temp.resolve("empty.pdf");
        service(TestAuth.admin()).export(FROM, TO, null, file, ReportFormat.PDF);
        assertTrue(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"));
        verify(dao).totals(FROM, TO, null);
    }

    private void stubReportRows() {
        when(dao.totals(any(), any(), any())).thenReturn(new CollectionTotals(2, bd("200"), bd("150"), bd("50"), bd("75.00"), bd("50"), bd("25")));
        when(dao.monthlyTrend(any(), any(), any())).thenReturn(List.of(new MonthlyTrend(YearMonth.of(2026, 10), bd("200"), bd("150"), bd("50"), bd("50"))));
        when(dao.byProperty(any(), any(), any())).thenReturn(List.of(new PropertyBreakdown(9, "Oak", 2, bd("200"), bd("150"), bd("50"), bd("50"))));
        when(dao.byTenant(any(), any(), any())).thenReturn(List.of(new TenantBreakdown(7, "Tenant Secret", "Oak", 2, bd("200"), bd("150"), bd("50"), bd("50"))));
        when(dao.arrears(any(), any())).thenReturn(List.of(new ArrearsBand("1-30 days", 1, bd("50"))));
        when(dao.followUp(any(), any(), anyBoolean())).thenReturn(List.of(new ChargeFollowUp(3, "Oak", "Tenant Secret", FROM, 25, bd("100"), bd("50"), bd("50"))));
        when(dao.paymentBehavior(any(), any(), any(), anyBoolean())).thenReturn(List.of(new LatePaymentBehavior(7, "Tenant Secret", "Oak", 3, 3, true, true)));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
