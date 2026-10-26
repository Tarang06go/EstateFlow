package com.estateflow.report;

import java.util.List;

/** Flat, permission-filtered rows for the rent collection CSV/PDF exports. */
public final class RentCollectionExportReport implements ReportDefinition<RentCollectionExportReport.Row> {
    public record Row(String section, String metric, String period, String property, String tenant,
                      String count, String amount, String notes) { }
    @Override public String fileName() { return "rent-collection-insights"; }
    @Override public List<String> headers() { return List.of("Section", "Metric", "Period", "Property", "Tenant", "Count", "Amount", "Notes"); }
    @Override public List<String> toRow(Row r) { return List.of(r.section(), r.metric(), r.period(), r.property(), r.tenant(), r.count(), r.amount(), r.notes()); }
}
