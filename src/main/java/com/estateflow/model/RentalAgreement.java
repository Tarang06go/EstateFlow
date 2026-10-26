package com.estateflow.model;

import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.model.enums.EffectiveAgreementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Links one property to one tenant for a rental period.
 * Owns effective-period logic (BR-12).
 */
public class RentalAgreement {

    private Long agreementId;
    private Long propertyId;
    private Long tenantId;
    private LocalDate startDate;
    private int durationMonths;
    private LocalDate endDate;
    private BigDecimal monthlyRent;
    private BigDecimal securityDeposit;
    private AgreementStatus status;
    private LocalDate terminationDate;
    private String notes;
    private LocalDateTime createdAt;

    public RentalAgreement() {
        this.status = AgreementStatus.ACTIVE;
        this.securityDeposit = BigDecimal.ZERO;
    }

    // --- Derived logic (BR-12) ---

    /**
     * Returns the effective end date: terminationDate if terminated, endDate otherwise.
     * This matches the database generated column.
     */
    public LocalDate getEffectiveEndDate() {
        return terminationDate != null ? terminationDate : endDate;
    }

    /**
     * Returns true if this agreement occupies its property on the given date.
     * An agreement occupies from startDate to effectiveEndDate inclusive.
     */
    public boolean occupies(LocalDate date) {
        Objects.requireNonNull(date, "date is required");
        return !date.isBefore(startDate) && !date.isAfter(getEffectiveEndDate());
    }

    /**
     * Returns true if this agreement's effective period intersects [from, to] inclusive.
     */
    public boolean overlaps(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        return !getEffectiveEndDate().isBefore(from) && !startDate.isAfter(to);
    }

    /**
     * Returns the derived effective status based on today and a warning window.
     */
    public EffectiveAgreementStatus effectiveStatus(LocalDate today, int warningDays) {
        Objects.requireNonNull(today, "today is required");
        // A lease that has been renewed is shown as RENEWED (not as expiring or expired) once it has started;
        // an early termination still wins.
        if (renewedByAgreementId != null && status != AgreementStatus.TERMINATED && !today.isBefore(startDate)) {
            return EffectiveAgreementStatus.RENEWED;
        }
        if (today.isBefore(startDate)) {
            return EffectiveAgreementStatus.UPCOMING;
        }
        LocalDate effectiveEnd = getEffectiveEndDate();
        if (today.isAfter(effectiveEnd)) {
            return status == AgreementStatus.TERMINATED
                    ? EffectiveAgreementStatus.TERMINATED
                    : EffectiveAgreementStatus.EXPIRED;
        }
        // today is within [startDate, effectiveEnd]: Section 5.4 uses dates only, so a terminated
        // agreement still serving its notice period is ACTIVE or EXPIRING_SOON.
        if (!effectiveEnd.isAfter(today.plusDays(warningDays))) {
            return EffectiveAgreementStatus.EXPIRING_SOON;
        }
        return EffectiveAgreementStatus.ACTIVE;
    }

    /** The agreement this one renewed, or null (first lease). Loaded from the renewal link; never set by hand. */
    private Long renewedFromAgreementId;
    /** The agreement that renewed this one, or null. */
    private Long renewedByAgreementId;

    public Long getRenewedFromAgreementId() { return renewedFromAgreementId; }
    public void setRenewedFromAgreementId(Long id) { this.renewedFromAgreementId = id; }
    public Long getRenewedByAgreementId() { return renewedByAgreementId; }
    public void setRenewedByAgreementId(Long id) { this.renewedByAgreementId = id; }

    // --- Getters and setters ---

    public Long getAgreementId() { return agreementId; }
    public void setAgreementId(Long agreementId) { this.agreementId = agreementId; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(BigDecimal monthlyRent) { this.monthlyRent = monthlyRent; }

    public BigDecimal getSecurityDeposit() { return securityDeposit; }
    public void setSecurityDeposit(BigDecimal securityDeposit) { this.securityDeposit = securityDeposit; }

    public AgreementStatus getStatus() { return status; }
    public void setStatus(AgreementStatus status) { this.status = status; }

    public LocalDate getTerminationDate() { return terminationDate; }
    public void setTerminationDate(LocalDate terminationDate) { this.terminationDate = terminationDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RentalAgreement that = (RentalAgreement) o;
        if (agreementId == null || that.agreementId == null) return false;
        return agreementId.equals(that.agreementId);
    }

    @Override
    public int hashCode() {
        return agreementId != null ? agreementId.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "RentalAgreement{agreementId=" + agreementId +
                ", propertyId=" + propertyId +
                ", tenantId=" + tenantId +
                ", status=" + status + "}";
    }
}
