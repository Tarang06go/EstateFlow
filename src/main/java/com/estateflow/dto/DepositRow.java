package com.estateflow.dto;

import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.model.enums.DepositStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An agreement's deposit position. depositId is null when nothing has been collected yet but a deposit is required. */
public record DepositRow(Long depositId, long agreementId, long propertyId, String propertyName, long tenantId,
                         String tenantName, BigDecimal required, DepositTotals totals, LocalDate agreementStart,
                         LocalDate effectiveEnd, AgreementStatus agreementStatus, boolean renewed,
                         LocalDate firstCollection) {

    public DepositStatus status() {
        if (depositId == null || totals.original().signum() == 0) {
            return DepositStatus.PENDING;
        }
        return totals.balance().signum() > 0 ? DepositStatus.HELD : DepositStatus.SETTLED;
    }

    /** The lease has ended (by date or early termination) as of the given day. */
    public boolean ended(LocalDate today) {
        return !today.isBefore(effectiveEnd);
    }
}
