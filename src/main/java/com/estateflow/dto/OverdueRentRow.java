package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OverdueRentRow(
        long chargeId, long agreementId, String propertyName, String tenantName, String tenantPhone,
        LocalDate periodStart, LocalDate dueDate, BigDecimal amountDue, BigDecimal amountPaid,
        BigDecimal balance, long daysOverdue) {
}
