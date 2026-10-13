package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An agreement whose effective period ends inside a date window. */
public record ExpiringLeaseRow(
        long agreementId, String propertyName, String tenantName, LocalDate effectiveEndDate,
        long daysRemaining, BigDecimal monthlyRent, boolean terminatedEarly) {
}
