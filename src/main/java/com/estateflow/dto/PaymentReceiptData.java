package com.estateflow.dto;

import com.estateflow.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentReceiptData(
        String receiptNumber, LocalDate paymentDate, String tenantName, String propertyName,
        LocalDate periodStart, LocalDate periodEnd, BigDecimal amount, PaymentMethod method,
        String referenceNo, BigDecimal currentBalance) {
}
