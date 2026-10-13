package com.estateflow.dto;

import com.estateflow.model.enums.AgreementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AgreementListRow(
        long agreementId, long propertyId, String propertyName, long tenantId, String tenantName,
        LocalDate startDate, LocalDate endDate, LocalDate effectiveEndDate, BigDecimal monthlyRent,
        AgreementStatus status, LocalDate terminationDate) {
}
