package com.estateflow.service;

import com.estateflow.dao.ReportingDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.dto.AdvancedAnalyticsReport;
import com.estateflow.dto.AdvancedAnalyticsReport.*;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.dto.MaintenanceSummary;
import com.estateflow.dto.ProfitabilityReport;
import com.estateflow.dto.PropertyOccupancyRow;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.exception.ValidationException;
import com.estateflow.report.AnalyticsExportReport;
import com.estateflow.report.CsvWriter;
import com.estateflow.report.ReportFormat;
import com.estateflow.report.pdf.PdfTableReport;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Portfolio analytics assembled from existing financial, property, lease and maintenance records. */
public final class AdvancedAnalyticsService {
    private static final int MAX_MONTHS = 120;
    private static final int FORECAST_MONTHS = 6;
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss'Z'");
    private static final List<String> AGE_BANDS = List.of("1-30 days", "31-60 days", "61-90 days", "Over 90 days");

    private final ReportingDao reportingDao;
    private final ProfitabilityService profitability;
    private final MaintenanceService maintenance;
    private final LeaseRenewalService leases;
    private final WorkOrderService workOrders;
    private final TransactionManager tx;
    private final CsvWriter csv;
    private final Clock clock;
    private final int leaseWarningDays;
    private final Authorizer auth;

    public AdvancedAnalyticsService(ReportingDao reportingDao, ProfitabilityService profitability,
                                   MaintenanceService maintenance, LeaseRenewalService leases,
                                   WorkOrderService workOrders, TransactionManager tx, CsvWriter csv,
                                   Clock clock, int leaseWarningDays, Authorizer auth) {
        this.reportingDao = reportingDao;
        this.profitability = profitability;
        this.maintenance = maintenance;
        this.leases = leases;
        this.workOrders = workOrders;
        this.tx = tx;
        this.csv = csv;
        this.clock = clock;
        this.leaseWarningDays = leaseWarningDays;
        this.auth = auth;
    }

    public AdvancedAnalyticsReport report(LocalDate from, LocalDate to) throws EstateFlowException {
        auth.require(Permission.VIEW_REPORTS);
        validateRange(from, to);
        LocalDate today = LocalDate.now(clock);
        return tx.inTransaction(() -> {
            ProfitabilityReport actual = profitability.report(from, to, null);
            List<PropertyOccupancyRow> propertyRows = reportingDao.findOccupancyRows(today);
            int active = (int) propertyRows.stream().filter(PropertyOccupancyRow::active).count();
            int occupied = (int) propertyRows.stream().filter(r -> r.active() && r.occupied()).count();
            BigDecimal occupancyRate = active == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(occupied * 100L).divide(BigDecimal.valueOf(active), 2, RoundingMode.HALF_UP);

            Map<String, ArrearsBucket> ages = new LinkedHashMap<>();
            for (ArrearsBucket bucket : reportingDao.arrearsAging(today)) ages.put(bucket.label(), bucket);
            List<ArrearsBucket> arrears = AGE_BANDS.stream().map(label -> ages.getOrDefault(label,
                    new ArrearsBucket(label, 0, BigDecimal.ZERO))).toList();

            List<ExpiringLeaseRow> expiring = leases.endingWithinWarningWindow(leaseWarningDays);
            Map<YearMonth, List<ExpiringLeaseRow>> expiryByMonth = new java.util.TreeMap<>();
            for (ExpiringLeaseRow lease : expiring) expiryByMonth.computeIfAbsent(YearMonth.from(lease.effectiveEndDate()), k -> new ArrayList<>()).add(lease);
            List<LeaseExpiryMonth> expiryMonths = expiryByMonth.entrySet().stream().map(e -> new LeaseExpiryMonth(e.getKey(),
                    e.getValue().size(), e.getValue().stream().map(ExpiringLeaseRow::monthlyRent).reduce(BigDecimal.ZERO, BigDecimal::add))).toList();
            BigDecimal rentAtRisk = expiring.stream().map(ExpiringLeaseRow::monthlyRent).reduce(BigDecimal.ZERO, BigDecimal::add);

            List<ForecastMonth> forecast = forecast(actual, YearMonth.from(today));
            MaintenanceSummary maintenanceSummary = maintenance.summary();
            boolean vendorAnalyticsAvailable = workOrders.isAvailable();
            List<VendorPerformance> vendorRows = vendorAnalyticsAvailable
                    ? workOrders.vendorPerformance(from, to) : List.of();
            return new AdvancedAnalyticsReport(from, to, reportingDao.collectionMetrics(from, to),
                    new OccupancySummary(active, occupied, active - occupied, occupancyRate), arrears,
                    actual, forecast, new LeaseExpirySummary(leaseWarningDays, expiring.size(), rentAtRisk, expiryMonths),
                    maintenanceSummary, vendorAnalyticsAvailable, vendorRows == null ? List.of() : List.copyOf(vendorRows));
        });
    }

    public int export(LocalDate from, LocalDate to, Path target, ReportFormat format) throws EstateFlowException {
        auth.require(Permission.VIEW_REPORTS);
        AdvancedAnalyticsReport report = report(from, to);
        List<AnalyticsExportReport.Row> rows = AnalyticsExportReport.rows(report);
        if (format == ReportFormat.PDF) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            String pdfDate = DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss'Z'").format(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            PdfTableReport.write(target, "Advanced portfolio analytics", from + " to " + to,
                    List.of("Actual collection rate is paid-to-date against charges due in the selected period.",
                            "Forecast is a simple six-month run-rate estimate from the selected actual period."),
                    new AnalyticsExportReport(), rows, GENERATED.format(now), pdfDate);
        } else {
            csv.write(target, new AnalyticsExportReport(), rows);
        }
        return rows.size();
    }

    private static List<ForecastMonth> forecast(ProfitabilityReport actual, YearMonth currentMonth) {
        int months = actual.months().size();
        BigDecimal averageRent = months == 0 ? BigDecimal.ZERO : actual.months().stream().map(ProfitabilityReport.MonthlyProfit::income)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal averageExpenses = months == 0 ? BigDecimal.ZERO : actual.months().stream().map(ProfitabilityReport.MonthlyProfit::expenses)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        List<ForecastMonth> result = new ArrayList<>();
        for (int i = 1; i <= FORECAST_MONTHS; i++) {
            result.add(new ForecastMonth(currentMonth.plusMonths(i), averageRent, averageExpenses,
                    averageRent.subtract(averageExpenses).setScale(2, RoundingMode.HALF_UP)));
        }
        return List.copyOf(result);
    }

    private static void validateRange(LocalDate from, LocalDate to) throws ValidationException {
        if (from == null || to == null) throw new ValidationException("Choose a start and an end date.", "dateRange");
        if (to.isBefore(from)) throw new ValidationException("The end date cannot be before the start date.", "dateRange");
        if (java.time.YearMonth.from(from).until(java.time.YearMonth.from(to), java.time.temporal.ChronoUnit.MONTHS) + 1 > MAX_MONTHS) {
            throw new ValidationException("Choose a period of at most 10 years.", "dateRange");
        }
    }
}
