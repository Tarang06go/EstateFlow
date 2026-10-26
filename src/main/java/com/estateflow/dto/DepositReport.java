package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deposit position and period activity. liability = refundable balances held now (a liability, never income).
 * collected/deducted/refunded cover the period only and exclude voided entries and renewal carry-overs.
 */
public record DepositReport(LocalDate from, LocalDate to, BigDecimal liability, BigDecimal collected, BigDecimal deducted,
                            BigDecimal refunded, List<DepositRow> rows, List<DepositActivityRow> activity) {

    public record GroupTotal(String label, int agreements, BigDecimal original, BigDecimal deducted, BigDecimal refunded,
                             BigDecimal transferredOut, BigDecimal balance) {
    }
}
