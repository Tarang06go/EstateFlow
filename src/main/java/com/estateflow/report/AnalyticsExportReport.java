package com.estateflow.report;

import com.estateflow.dto.AdvancedAnalyticsReport;

import java.util.ArrayList;
import java.util.List;

/** Flattened multi-section analytics export for CSV and the shared paginated PDF renderer. */
public final class AnalyticsExportReport implements ReportDefinition<AnalyticsExportReport.Row> {
    public record Row(String section, String metric, String period, String count, String amount, String rateOrNotes) { }

    @Override public String fileName() { return "advanced-analytics"; }
    @Override public List<String> headers() { return List.of("Section", "Metric", "Period / name", "Count", "Amount", "Rate / notes"); }
    @Override public List<String> toRow(Row row) { return List.of(row.section(), row.metric(), row.period(), row.count(), row.amount(), row.rateOrNotes()); }

    public static List<Row> rows(AdvancedAnalyticsReport report) {
        List<Row> rows = new ArrayList<>();
        var c = report.collection();
        rows.add(new Row("Rent collection", "Billed on charges due", report.from() + " to " + report.to(),
                String.valueOf(c.chargeCount()), c.billed().toPlainString(), "Due-date period"));
        rows.add(new Row("Rent collection", "Collected to date", report.from() + " to " + report.to(),
                c.fullyCollectedCount() + " of " + c.chargeCount() + " fully paid", c.collectedToDate().toPlainString(), c.ratePercent().toPlainString() + "% of billed"));
        rows.add(new Row("Rent collection", "Outstanding on those charges", report.from() + " to " + report.to(), "", c.outstanding().toPlainString(), "Current balance"));
        var o = report.occupancy();
        rows.add(new Row("Occupancy", "Active properties", "Today", String.valueOf(o.activeProperties()), "", o.percent().toPlainString() + "% occupied"));
        rows.add(new Row("Occupancy", "Occupied / vacant", "Today", o.occupiedProperties() + " / " + o.vacantProperties(), "", "Active properties only"));
        for (var age : report.arrears()) rows.add(new Row("Arrears aging", age.label(), "Today", String.valueOf(age.charges()), age.balance().toPlainString(), "Overdue live charges"));
        var p = report.profitability();
        rows.add(new Row("Actual cash basis", "Rent charged", report.from() + " to " + report.to(), "", p.rentCharged().toPlainString(), "Due date"));
        rows.add(new Row("Actual cash basis", "Rent received", report.from() + " to " + report.to(), "", p.rentReceived().toPlainString(), "Payment date"));
        rows.add(new Row("Actual cash basis", "Operating expenses", report.from() + " to " + report.to(), "", p.expenses().toPlainString(), "Expense date / resolved maintenance date"));
        rows.add(new Row("Actual cash basis", "NOI", report.from() + " to " + report.to(), "", p.noi().toPlainString(), "Rent received minus operating expenses"));
        for (var month : p.months()) rows.add(new Row("Actual monthly", "Cash flow", month.month().toString(), "",
                month.noi().toPlainString(), "Received=" + month.income().toPlainString() + "; expenses=" + month.expenses().toPlainString()));
        for (var month : report.forecast()) rows.add(new Row("Forecast estimate", "Run rate", month.month().toString(), "",
                month.estimatedNoi().toPlainString(), "Rent=" + month.estimatedRentReceived().toPlainString() + "; expenses=" + month.estimatedOperatingExpenses().toPlainString()));
        var expiry = report.leaseExpiry();
        rows.add(new Row("Lease expiry", "Ending within configured warning window", "Next " + expiry.warningDays() + " days",
                String.valueOf(expiry.leasesEnding()), expiry.monthlyRentAtRisk().toPlainString(), "Monthly rent at risk"));
        for (var month : expiry.byMonth()) rows.add(new Row("Lease expiry", "Ending", month.month().toString(),
                String.valueOf(month.leasesEnding()), month.monthlyRentAtRisk().toPlainString(), "Monthly rent at risk"));
        var maintenance = report.maintenance();
        rows.add(new Row("Maintenance", "Requests", "All time", String.valueOf(maintenance.totalRequests()), "", "By status:" + maintenance.countByStatus()));
        rows.add(new Row("Maintenance", "Recorded costs", "Current summary", "", maintenance.totalCost().toPlainString(),
                "Resolved=" + maintenance.resolvedCost().toPlainString() + "; in progress=" + maintenance.inProgressCost().toPlainString()));
        for (var v : report.vendorPerformance()) rows.add(new Row("Vendor performance", v.vendorName(), report.from() + " to " + report.to(),
                String.valueOf(v.completedOrders()), v.actualCost().toPlainString(), "On time=" + v.onTimeOrders() + "/" + v.dueDatedOrders() + " due-dated (" + v.onTimePercent() + "%); avg days=" + v.averageDaysToComplete()));
        if (!report.vendorAnalyticsAvailable()) rows.add(new Row("Vendor performance", "Unavailable", report.from() + " to " + report.to(), "", "", "Work-order schema is unavailable; apply v1.7 using the documented migration process"));
        else if (report.vendorPerformance().isEmpty()) rows.add(new Row("Vendor performance", "No matching completed work", report.from() + " to " + report.to(), "0", "", "No completed vendor-assigned work orders match this completion period"));
        return List.copyOf(rows);
    }
}
