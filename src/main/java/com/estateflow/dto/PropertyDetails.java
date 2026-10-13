package com.estateflow.dto;

import com.estateflow.model.Property;

import java.math.BigDecimal;
import java.util.List;

/** Everything the property detail screen shows, loaded in one service call. */
public record PropertyDetails(
        Property property, String ownerName, String ownerPhone, AgreementListRow currentAgreement,
        List<AgreementListRow> agreements, List<PaymentReportRow> payments, List<MaintenanceReportRow> maintenance,
        BigDecimal outstandingBalance, BigDecimal maintenanceCost) {
}
