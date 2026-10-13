package com.estateflow.report;

import com.estateflow.dto.MaintenanceReportRow;

import java.util.List;

public class MaintenanceReport implements ReportDefinition<MaintenanceReportRow> {
    public String fileName() {
        return "maintenance.csv";
    }

    public List<String> headers() {
        return List.of("Request", "Property", "Tenant", "Title", "Priority", "Status", "Reported",
                "Resolved", "Assigned To", "Cost");
    }

    public List<String> toRow(MaintenanceReportRow r) {
        return List.of(String.valueOf(r.requestId()), r.propertyName(), nz(r.tenantName()), r.title(),
                r.priority().name(), r.status().name(), String.valueOf(r.reportedDate()),
                r.resolvedDate() == null ? "" : r.resolvedDate().toString(), nz(r.assignedTo()),
                r.cost() == null ? "" : r.cost().toPlainString());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
