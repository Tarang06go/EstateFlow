package com.estateflow.report;

import com.estateflow.dto.AgreementListRow;
import com.estateflow.util.DateFormats;

import java.util.List;

public class AgreementsReport implements ReportDefinition<AgreementListRow> {
    public String fileName() {
        return "agreements.csv";
    }

    public List<String> headers() {
        return List.of("No.", "Property", "Tenant", "Start", "Effective End", "Monthly Rent", "Status");
    }

    public List<String> toRow(AgreementListRow r) {
        return List.of(String.valueOf(r.agreementId()), r.propertyName(), r.tenantName(), DateFormats.format(r.startDate()),
                DateFormats.format(r.effectiveEndDate()), r.monthlyRent().toPlainString(),
                r.status() == com.estateflow.model.enums.AgreementStatus.TERMINATED ? "Terminated" : "Active");
    }
}
