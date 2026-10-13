package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Joined data a receipt needs beyond the payment itself. */
public record ReceiptContext(
        String tenantName, String propertyName, LocalDate periodStart, LocalDate periodEnd,
        BigDecimal currentBalance) {
}
