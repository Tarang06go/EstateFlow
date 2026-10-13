package com.estateflow.dto;

import com.estateflow.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentReportRow(
        long paymentId, String receiptNumber, LocalDate paymentDate, String tenantName, String propertyName,
        LocalDate periodStart, BigDecimal amount, PaymentMethod method, String referenceNo,
        boolean voided, String voidReason) {
}
