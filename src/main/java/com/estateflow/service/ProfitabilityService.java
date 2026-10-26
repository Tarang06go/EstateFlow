package com.estateflow.service;

import com.estateflow.dao.ProfitabilityDao;
import com.estateflow.dao.ProfitabilityDao.*;
import com.estateflow.dao.TransactionManager;
import com.estateflow.dto.ProfitabilityReport;
import com.estateflow.dto.ProfitabilityReport.*;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.exception.ReportExportException;
import com.estateflow.exception.ValidationException;
import com.estateflow.model.enums.ExpenseCategory;
import com.estateflow.report.CsvWriter;
import com.estateflow.report.PropertyProfitabilityReport;
import com.estateflow.report.ReportFormat;
import com.estateflow.report.pdf.PdfTableReport;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.util.DateFormats;
import com.estateflow.util.MoneyUtil;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Rental profitability on a cash basis.
 * <ul>
 *   <li>Rent received = valid (not voided) rent payments dated in the range. Charged rent is reported separately.</li>
 *   <li>Every payment belongs to a rent charge, so security deposits and other non-rent money are never included.</li>
 *   <li>Expenses = active expenses dated in the range + the cost of RESOLVED maintenance requests (resolved date in the
 *       range) that have no active expense linked to them, so a repair is never counted twice.</li>
 *   <li>NOI = rent received - operating expenses. It excludes financing, tax on income, depreciation and capital items
 *       and is not total profit.</li>
 * </ul>
 * All figures are read in one transaction (one consistent snapshot). Requires VIEW_REPORTS.
 */
public class ProfitabilityService {

    static final int MAX_MONTHS = 120;
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss'Z'");

    private final ProfitabilityDao dao;
    private final CsvWriter csvWriter;
    private final TransactionManager tx;
    private final Clock clock;
    private final Authorizer auth;

    public ProfitabilityService(ProfitabilityDao dao, CsvWriter csvWriter, TransactionManager tx, Clock clock, Authorizer auth) {
        this.dao = dao;
        this.csvWriter = csvWriter;
        this.tx = tx;
        this.clock = clock;
        this.auth = auth;
    }

    public ProfitabilityReport report(LocalDate from, LocalDate to, Long propertyIdOrNull) throws EstateFlowException {
        auth.require(Permission.VIEW_REPORTS);
        if (from == null || to == null) {
            throw new ValidationException("Choose a start and an end date.", "dateRange");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("The end date cannot be before the start date.", "dateRange");
        }
        YearMonth first = YearMonth.from(from);
        YearMonth last = YearMonth.from(to);
        if (first.until(last, java.time.temporal.ChronoUnit.MONTHS) + 1 > MAX_MONTHS) {
            throw new ValidationException("Choose a period of at most 10 years.", "dateRange");
        }

        record Raw(List<PropertyRef> props, List<IncomeRow> income, List<ExpenseAggRow> expenses, List<ChargedRow> charged) {
        }
        Raw raw = tx.inTransaction(() -> new Raw(dao.properties(propertyIdOrNull), dao.receivedRent(from, to, propertyIdOrNull),
                dao.expenses(from, to, propertyIdOrNull), dao.chargedRent(from, to, propertyIdOrNull)));

        Map<Long, BigDecimal> receivedBy = new HashMap<>(), expensesBy = new HashMap<>(), chargedBy = new HashMap<>();
        Map<YearMonth, BigDecimal> incomeByMonth = new HashMap<>(), expenseByMonth = new HashMap<>();
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        BigDecimal received = BigDecimal.ZERO, spent = BigDecimal.ZERO, charged = BigDecimal.ZERO, fromRequests = BigDecimal.ZERO;

        for (IncomeRow r : raw.income()) {
            receivedBy.merge(r.propertyId(), r.amount(), BigDecimal::add);
            incomeByMonth.merge(r.month(), r.amount(), BigDecimal::add);
            received = received.add(r.amount());
        }
        for (ExpenseAggRow r : raw.expenses()) {
            expensesBy.merge(r.propertyId(), r.amount(), BigDecimal::add);
            expenseByMonth.merge(r.month(), r.amount(), BigDecimal::add);
            byCategory.merge(r.category(), r.amount(), BigDecimal::add);
            spent = spent.add(r.amount());
            if (r.fromMaintenanceRequest()) {
                fromRequests = fromRequests.add(r.amount());
            }
        }
        for (ChargedRow r : raw.charged()) {
            chargedBy.merge(r.propertyId(), r.amount(), BigDecimal::add);
            charged = charged.add(r.amount());
        }

        List<PropertyProfit> properties = new ArrayList<>();
        for (PropertyRef p : raw.props()) {
            BigDecimal rec = receivedBy.getOrDefault(p.propertyId(), BigDecimal.ZERO);
            BigDecimal exp = expensesBy.getOrDefault(p.propertyId(), BigDecimal.ZERO);
            BigDecimal chg = chargedBy.getOrDefault(p.propertyId(), BigDecimal.ZERO);
            boolean activity = rec.signum() != 0 || exp.signum() != 0 || chg.signum() != 0;
            if (p.active() || activity) {
                properties.add(new PropertyProfit(p.propertyId(), p.name(), p.active(), money(chg), money(rec), money(exp),
                        money(rec.subtract(exp))));
            }
        }
        properties.sort(Comparator.comparing(PropertyProfit::noi).reversed().thenComparing(PropertyProfit::propertyName));

        List<MonthlyProfit> months = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            months.add(new MonthlyProfit(m, money(incomeByMonth.getOrDefault(m, BigDecimal.ZERO)),
                    money(expenseByMonth.getOrDefault(m, BigDecimal.ZERO))));
        }
        List<CategoryAmount> categories = byCategory.entrySet().stream()
                .filter(e -> e.getValue().signum() != 0)
                .map(e -> new CategoryAmount(e.getKey(), money(e.getValue())))
                .sorted(Comparator.comparing(CategoryAmount::amount).reversed()).toList();

        return new ProfitabilityReport(from, to, propertyIdOrNull, money(charged), money(received), money(spent),
                money(received.subtract(spent)), money(fromRequests), categories, properties, months);
    }

    /** Property-wise CSV or PDF (rent charged, rent received, expenses, NOI) with a TOTAL row. */
    public int exportProperties(LocalDate from, LocalDate to, Long propertyIdOrNull, Path target, ReportFormat format)
            throws EstateFlowException {
        auth.require(Permission.VIEW_REPORTS);
        ProfitabilityReport r = report(from, to, propertyIdOrNull);
        List<PropertyProfit> rows = new ArrayList<>(r.properties());
        rows.add(new PropertyProfit(0, "TOTAL", true, r.rentCharged(), r.rentReceived(), r.expenses(), r.noi()));
        try {
            if (format == ReportFormat.PDF) {
                LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
                String pdfDate = PDF_DATE.format(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
                PdfTableReport.write(target, "Property profitability",
                        "Cash basis, " + DateFormats.format(from) + " to " + DateFormats.format(to),
                        List.of("Rent received: Rs. " + MoneyUtil.format(r.rentReceived()) + "   Expenses: Rs. "
                                        + MoneyUtil.format(r.expenses()) + "   NOI: Rs. " + MoneyUtil.format(r.noi()),
                                "Rent charged (billed, not necessarily received): Rs. " + MoneyUtil.format(r.rentCharged()),
                                "NOI = rent received - operating expenses. Financing, income tax and capital items are excluded; "
                                        + "NOI is not total profit."),
                        new PropertyProfitabilityReport(), rows, GENERATED.format(now), pdfDate);
            } else {
                csvWriter.write(target, new PropertyProfitabilityReport(), rows);
            }
        } catch (ReportExportException e) {
            throw e;
        }
        return r.properties().size();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
