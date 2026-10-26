package com.estateflow.service;

import com.estateflow.dao.RentCollectionInsightsDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.dto.RentCollectionInsightsReport;
import com.estateflow.dto.RentCollectionInsightsReport.*;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.exception.ValidationException;
import com.estateflow.report.CsvWriter;
import com.estateflow.report.ReportFormat;
import com.estateflow.report.RentCollectionExportReport;
import com.estateflow.report.RentCollectionExportReport.Row;
import com.estateflow.report.pdf.PdfTableReport;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Collection and arrears insights built from the same live charges and valid payments as the rent ledger. */
public final class RentCollectionInsightsService {
    private static final int MAX_MONTHS = 120;
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private final RentCollectionInsightsDao dao;
    private final TransactionManager tx;
    private final Clock clock;
    private final CsvWriter csv;
    private final Authorizer auth;

    public RentCollectionInsightsService(RentCollectionInsightsDao dao, TransactionManager tx, Clock clock,
                                         CsvWriter csv, Authorizer auth) {
        this.dao = dao; this.tx = tx; this.clock = clock; this.csv = csv; this.auth = auth;
    }

    public RentCollectionInsightsReport report(LocalDate from, LocalDate to, Long propertyId) throws EstateFlowException {
        auth.require(Permission.VIEW_REPORTS);
        validate(from, to, propertyId);
        LocalDate today = LocalDate.now(clock);
        LocalDate behaviorFrom = from.isAfter(to.minusMonths(12).plusDays(1)) ? from : to.minusMonths(12).plusDays(1);
        boolean tenantAccess = auth.can(Permission.VIEW_TENANT_INSIGHTS);
        return tx.inTransaction(() -> {
            List<ChargeFollowUp> followUp = dao.followUp(today, propertyId, tenantAccess);
            if (!tenantAccess) {
                followUp = followUp.stream().map(row -> new ChargeFollowUp(row.chargeId(), row.propertyName(), null,
                        row.dueDate(), row.daysOverdue(), row.amountDue(), row.amountPaid(), row.balance())).toList();
            }
            return new RentCollectionInsightsReport(from, to, today, propertyId,
                    dao.totals(from, to, propertyId), dao.monthlyTrend(from, to, propertyId),
                    dao.byProperty(from, to, propertyId), tenantAccess ? dao.byTenant(from, to, propertyId) : List.of(),
                    dao.arrears(today, propertyId), followUp,
                    tenantAccess ? dao.paymentBehavior(behaviorFrom, to, propertyId, true) : List.of(), behaviorFrom, to);
        });
    }

    /** Re-reads a fresh snapshot and rechecks role access immediately before exporting. */
    public int export(LocalDate from, LocalDate to, Long propertyId, Path target, ReportFormat format) throws EstateFlowException {
        auth.require(Permission.VIEW_REPORTS);
        RentCollectionInsightsReport report = report(from, to, propertyId);
        List<Row> rows = rows(report);
        if (format == ReportFormat.PDF) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            PdfTableReport.write(target, "Rent collection insights", from + " to " + to,
                    List.of("Charges use live rent charges due in the selected period. Collection is applied to those charges through " + to + ".",
                            "Period receipts use valid payments dated in the period; current arrears are aged from " + report.asOf() + ".",
                            "Late history includes fully settled charges only; insufficient history is not treated as a late-payment pattern."),
                    new RentCollectionExportReport(), rows, GENERATED.format(now),
                    DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss'Z'").format(LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)));
        } else {
            csv.write(target, new RentCollectionExportReport(), rows);
        }
        return rows.size();
    }

    private static List<Row> rows(RentCollectionInsightsReport r) {
        List<Row> rows = new ArrayList<>();
        CollectionTotals t = r.totals();
        rows.add(new Row("Period collection", "Rent charges due", r.from() + " to " + r.to(), "", "", String.valueOf(t.chargeCount()), t.chargesDue().toPlainString(), "Live charges by due date"));
        rows.add(new Row("Period collection", "Collected on selected charges", r.from() + " to " + r.to(), "", "", "", t.collectedOnSelectedCharges().toPlainString(), "Valid payments through period end"));
        rows.add(new Row("Period collection", "Outstanding on selected charges", r.from() + " to " + r.to(), "", "", "", t.outstandingOnSelectedCharges().toPlainString(), "Charges minus valid payments, floored at zero"));
        rows.add(new Row("Period collection", "Collection rate", r.from() + " to " + r.to(), "", "", "", t.collectionPercent().toPlainString() + "%", "Collected on selected charges / charges due; zero when no charges"));
        rows.add(new Row("Cash receipts", "Payments received", r.from() + " to " + r.to(), "", "", "", t.paymentsReceivedInPeriod().toPlainString(), "Valid payment date in selected period"));
        rows.add(new Row("Cash receipts", "Applied to earlier due charges", r.from() + " to " + r.to(), "", "", "", t.paymentsAppliedToEarlierCharges().toPlainString(), "Charge due date before period start"));
        for (MonthlyTrend m : r.monthlyTrend()) rows.add(new Row("Monthly trend", "Collection", m.month().toString(), "", "", "", m.chargesDue().toPlainString(),
                "Collected=" + m.collectedOnSelectedCharges() + "; outstanding=" + m.outstanding() + "; receipts=" + m.paymentsReceived()));
        for (PropertyBreakdown p : r.properties()) rows.add(new Row("Property", "Collection", r.from() + " to " + r.to(), p.propertyName(), "", String.valueOf(p.chargeCount()), p.chargesDue().toPlainString(),
                "Collected=" + p.collected() + "; outstanding=" + p.outstanding() + "; receipts=" + p.paymentsReceived()));
        for (TenantBreakdown p : r.tenants()) rows.add(new Row("Tenant detail", "Collection", r.from() + " to " + r.to(), p.propertyName(), p.tenantName(), String.valueOf(p.chargeCount()), p.chargesDue().toPlainString(),
                "Collected=" + p.collected() + "; outstanding=" + p.outstanding() + "; receipts=" + p.paymentsReceived()));
        for (ArrearsBand b : r.arrears()) rows.add(new Row("Current arrears", b.label(), r.asOf().toString(), "", "", String.valueOf(b.chargeCount()), b.balance().toPlainString(), "Outstanding live charges"));
        for (ChargeFollowUp f : r.followUp()) rows.add(new Row("Follow-up", "Overdue charge " + f.chargeId(), f.dueDate().toString(), f.propertyName(), f.tenantName() == null ? "" : f.tenantName(), "" + f.daysOverdue() + " days", f.balance().toPlainString(), "Amount due=" + f.amountDue() + "; paid=" + f.amountPaid()));
        for (LatePaymentBehavior p : r.paymentBehavior()) rows.add(new Row("Payment behavior", p.sufficientHistory() ? (p.repeatedLatePattern() ? "Repeated late settlements" : "Settled-charge history") : "Insufficient history",
                r.behaviorFrom() + " to " + r.behaviorTo(), p.propertyName(), p.tenantName(), String.valueOf(p.fullySettledCharges()), "", "Late settled charges=" + p.lateCharges() + "; minimum history=3; descriptive only"));
        return List.copyOf(rows);
    }

    private static void validate(LocalDate from, LocalDate to, Long propertyId) throws ValidationException {
        if (from == null || to == null) throw new ValidationException("Choose a start and end date.", "dateRange");
        if (to.isBefore(from)) throw new ValidationException("The end date cannot be before the start date.", "dateRange");
        if (java.time.YearMonth.from(from).until(java.time.YearMonth.from(to), java.time.temporal.ChronoUnit.MONTHS) + 1 > MAX_MONTHS)
            throw new ValidationException("Choose a period of at most 10 years.", "dateRange");
        if (propertyId != null && propertyId <= 0) throw new ValidationException("Choose a valid property filter.", "property");
    }
}
