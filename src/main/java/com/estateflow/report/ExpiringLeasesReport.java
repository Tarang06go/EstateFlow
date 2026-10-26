package com.estateflow.report;

import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.util.DateFormats;

import java.util.List;

public class ExpiringLeasesReport implements ReportDefinition<ExpiringLeaseRow> {
    public String fileName() {
        return "expiring-leases.csv";
    }

    public List<String> headers() {
        return List.of("Agreement", "Property", "Tenant", "Ends", "Days Left", "Monthly Rent", "Note");
    }

    public List<String> toRow(ExpiringLeaseRow r) {
        return List.of(String.valueOf(r.agreementId()), r.propertyName(), r.tenantName(), DateFormats.format(r.effectiveEndDate()),
                String.valueOf(r.daysRemaining()), r.monthlyRent().toPlainString(), r.terminatedEarly() ? "Terminated early" : "");
    }
}
