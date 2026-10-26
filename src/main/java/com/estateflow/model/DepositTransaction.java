package com.estateflow.model;

import com.estateflow.model.enums.DepositType;
import com.estateflow.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One immutable ledger entry. A mistake is voided (voidedAt, voidReason), never edited or deleted. */
public record DepositTransaction(Long txnId, long depositId, DepositType type, BigDecimal amount, LocalDate date,
                                 PaymentMethod method, String referenceNo, String reason, String notes,
                                 Long relatedAgreementId, LocalDateTime createdAt, LocalDateTime voidedAt, String voidReason) {
    public boolean isVoided() {
        return voidedAt != null;
    }
}
