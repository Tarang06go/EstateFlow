package com.estateflow.dto;

import java.math.BigDecimal;

/** Sums of the non-voided ledger entries of one deposit. */
public record DepositTotals(BigDecimal collected, BigDecimal transferredIn, BigDecimal deducted, BigDecimal refunded,
                            BigDecimal transferredOut) {

    public static final DepositTotals EMPTY = new DepositTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO);

    /** Original amount: everything that ever came in (collected plus carried in). */
    public BigDecimal original() {
        return collected.add(transferredIn);
    }

    /** Refundable balance still held. */
    public BigDecimal balance() {
        return original().subtract(deducted).subtract(refunded).subtract(transferredOut);
    }
}
