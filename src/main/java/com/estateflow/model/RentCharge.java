package com.estateflow.model;

import com.estateflow.model.enums.ChargeStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One month's rent obligation. Carries a read-only amountPaid field
 * populated by DAO queries (sum of valid payments).
 */
public class RentCharge {

    private Long chargeId;
    private Long agreementId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate dueDate;
    private BigDecimal amountDue;
    private LocalDateTime cancelledAt;

    /** Sum of valid (non-voided) payments. Populated by DAO, not persisted. */
    private BigDecimal amountPaid = BigDecimal.ZERO;

    public RentCharge() {}

    public RentCharge(Long agreementId, LocalDate periodStart, LocalDate periodEnd,
                      LocalDate dueDate, BigDecimal amountDue) {
        this.agreementId = Objects.requireNonNull(agreementId, "agreementId is required");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart is required");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd is required");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate is required");
        this.amountDue = Objects.requireNonNull(amountDue, "amountDue is required");
    }

    /**
     * Returns the remaining balance: amountDue - amountPaid.
     */
    public BigDecimal getBalance() {
        return amountDue.subtract(amountPaid);
    }

    /**
     * A charge is payable if it is live (not cancelled) and due_date <= today (BR-20).
     */
    public boolean isPayable(LocalDate today) {
        Objects.requireNonNull(today, "today is required");
        return cancelledAt == null && !dueDate.isAfter(today);
    }

    /**
     * Derives the charge status based on payment state and date (Section 5.4).
     */
    public ChargeStatus status(LocalDate today) {
        Objects.requireNonNull(today, "today is required");
        if (cancelledAt != null) {
            return ChargeStatus.CANCELLED;
        }
        BigDecimal balance = getBalance();
        if (balance.compareTo(BigDecimal.ZERO) == 0) {
            return ChargeStatus.PAID;
        }
        if (dueDate.isBefore(today)) {
            return ChargeStatus.OVERDUE;
        }
        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            return ChargeStatus.PARTIALLY_PAID;
        }
        return ChargeStatus.PENDING;
    }

    // --- Getters and setters ---

    public Long getChargeId() { return chargeId; }
    public void setChargeId(Long chargeId) { this.chargeId = chargeId; }

    public Long getAgreementId() { return agreementId; }
    public void setAgreementId(Long agreementId) { this.agreementId = agreementId; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RentCharge that = (RentCharge) o;
        if (chargeId == null || that.chargeId == null) return false;
        return chargeId.equals(that.chargeId);
    }

    @Override
    public int hashCode() {
        return chargeId != null ? chargeId.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "RentCharge{chargeId=" + chargeId +
                ", agreementId=" + agreementId +
                ", periodStart=" + periodStart +
                ", amountDue=" + amountDue + "}";
    }
}
