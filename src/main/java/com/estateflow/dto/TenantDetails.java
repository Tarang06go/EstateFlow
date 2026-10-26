package com.estateflow.dto;

import com.estateflow.model.Tenant;

import java.math.BigDecimal;
import java.util.List;

public record TenantDetails(Tenant tenant, List<AgreementListRow> agreements, List<PaymentReportRow> payments,
                            List<MaintenanceReportRow> maintenance, BigDecimal outstandingBalance) {
}
