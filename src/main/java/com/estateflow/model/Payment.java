package com.estateflow.model;

import com.estateflow.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An immutable payment record against one rent charge.
 * Payment facts have no setters. Void state changes only through markVoided().
 */
public class Payment {

    private Long paymentId;
    private Long chargeId;
    private final BigDecimal amount;
    private final LocalDate paymentDate;
    private final PaymentMethod paymentMethod;
    private final String referenceNo;
    private final String notes;
    private LocalDateTime voidedAt;
    private String voidReason;
    private LocalDateTime createdAt;

    public Payment(Long chargeId, BigDecimal amount, LocalDate paymentDate,
                   PaymentMethod paymentMethod, String referenceNo, String notes) {
        this.chargeId = Objects.requireNonNull(chargeId, "chargeId is required");
        this.amount = Objects.requireNonNull(amount, "amount is required");
        this.paymentDate = Objects.requireNonNull(paymentDate, "paymentDate is required");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod is required");
        this.referenceNo = referenceNo;
        this.notes = notes;
    }

    /**
     * Void this payment with a mandatory reason.
     * @throws IllegalStateException if already voided
     */
    public void markVoided(String reason, LocalDateTime at) {
        if (voidedAt != null) {
            throw new IllegalStateException("Payment " + paymentId + " is already voided");
        }
        Objects.requireNonNull(reason, "void reason is required");
        Objects.requireNonNull(at, "void timestamp is required");
        this.voidReason = reason;
        this.voidedAt = at;
    }

    public boolean isVoid() {
        return voidedAt != null;
    }

    /**
     * Receipt number: RCP-<paymentDate year>-<id padded to 6>.
     * Year comes from paymentDate, not createdAt.
     */
    public String receiptNumber() {
        if (paymentId == null) {
            throw new IllegalStateException("Cannot generate receipt number before payment is persisted");
        }
        return formatReceiptNumber(paymentDate, paymentId);
    }

    /** Receipt number format shared with joined report queries: RCP-&lt;payment year&gt;-&lt;id padded to 6&gt;. */
    public static String formatReceiptNumber(LocalDate paymentDate, long paymentId) {
        return String.format("RCP-%d-%06d", paymentDate.getYear(), paymentId);
    }

    // --- Getters (no setters for payment facts) ---

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public Long getChargeId() { return chargeId; }
    public void setChargeId(Long chargeId) { this.chargeId = chargeId; }

    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getReferenceNo() { return referenceNo; }
    public String getNotes() { return notes; }

    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime voidedAt) { this.voidedAt = voidedAt; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        if (paymentId == null || payment.paymentId == null) return false;
        return paymentId.equals(payment.paymentId);
    }

    @Override
    public int hashCode() {
        return paymentId != null ? paymentId.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "Payment{paymentId=" + paymentId +
                ", chargeId=" + chargeId +
                ", amount=" + amount +
                ", void=" + isVoid() + "}";
    }
}
