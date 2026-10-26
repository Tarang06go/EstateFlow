package com.estateflow.service;

import com.estateflow.dao.*;
import com.estateflow.dto.DepositTotals;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.dto.LeaseAttentionRow;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.model.enums.DepositType;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.service.rules.RentScheduleGenerator;
import com.estateflow.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Renewal and extension of rental agreements, on top of the existing agreement model.
 * <ul>
 *   <li><b>Renew</b> creates a NEW agreement that starts the day after the old one ends and is linked to it; the old
 *       agreement is left exactly as it was. A held deposit can be carried over to the new agreement (a ledger transfer,
 *       not new money).</li>
 *   <li><b>Extend</b> lengthens an agreement that has not ended, at the same rent, and adds the extra rent periods. The
 *       previous end date is kept in the lease history.</li>
 * </ul>
 * Both need MANAGE_RECORDS, re-check overlaps with the property locked, and record the acting user in the lease history
 * and the audit trail in one transaction. Reads need VIEW_DATA.
 */
public class LeaseRenewalService {

    /** An ended lease can still be renewed for this many days; after that, create a new agreement. */
    public static final int RENEWAL_GRACE_DAYS = 30;
    static final int MAX_MONTHS = 120;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final AgreementDao agreementDao;
    private final LeaseDao leaseDao;
    private final RentChargeDao chargeDao;
    private final PropertyDao propertyDao;
    private final TenantDao tenantDao;
    private final DepositDao depositDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;
    private final Authorizer auth;

    public LeaseRenewalService(AgreementDao agreementDao, LeaseDao leaseDao, RentChargeDao chargeDao, PropertyDao propertyDao,
                               TenantDao tenantDao, DepositDao depositDao, TransactionManager tx, AuditService audit, Clock clock,
                               Authorizer auth) {
        this.agreementDao = agreementDao;
        this.leaseDao = leaseDao;
        this.chargeDao = chargeDao;
        this.propertyDao = propertyDao;
        this.tenantDao = tenantDao;
        this.depositDao = depositDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.auth = auth;
    }

    /** Fails with a setup message when the v1.5 migration has not been applied. */
    public void ensureReady() {
        auth.require(Permission.VIEW_DATA);
        if (!leaseDao.supportsRenewal() || !depositDao.isAvailable()) {
            throw new ConfigurationException("Lease renewal and deposits need db/migrations/v1.5_deposits_leases.sql applied to the "
                    + "estateflow database (see README), then reopen this screen.");
        }
    }

    // ---------------------------------------------------------------- eligibility

    /** Why the agreement cannot be renewed now, or empty when it can. Shown by the UI and enforced by {@link #renew}. */
    public Optional<String> whyNotRenewable(long agreementId) throws EntityNotFoundException {
        auth.require(Permission.VIEW_DATA);
        return renewalBlocker(load(agreementId));
    }

    /** Why the agreement cannot be extended now, or empty when it can. */
    public Optional<String> whyNotExtendable(long agreementId) throws EntityNotFoundException {
        auth.require(Permission.VIEW_DATA);
        return extensionBlocker(load(agreementId));
    }

    private Optional<String> commonBlocker(RentalAgreement a) {
        LocalDate today = LocalDate.now(clock);
        if (a.getStatus() == AgreementStatus.TERMINATED) {
            return Optional.of("Terminated agreements cannot be renewed or extended.");
        }
        Optional<Long> successor = leaseDao.findSuccessor(a.getAgreementId());
        if (successor.isPresent()) {
            return Optional.of("This agreement has already been renewed by agreement " + successor.get() + ".");
        }
        if (today.isBefore(a.getStartDate())) {
            return Optional.of("This agreement has not started yet.");
        }
        return Optional.empty();
    }

    private Optional<String> renewalBlocker(RentalAgreement a) {
        Optional<String> common = commonBlocker(a);
        if (common.isPresent()) {
            return common;
        }
        if (LocalDate.now(clock).isAfter(a.getEffectiveEndDate().plusDays(RENEWAL_GRACE_DAYS))) {
            return Optional.of("This agreement ended more than " + RENEWAL_GRACE_DAYS + " days ago. Create a new agreement instead.");
        }
        return Optional.empty();
    }

    private Optional<String> extensionBlocker(RentalAgreement a) {
        Optional<String> common = commonBlocker(a);
        if (common.isPresent()) {
            return common;
        }
        if (LocalDate.now(clock).isAfter(a.getEffectiveEndDate())) {
            return Optional.of("This agreement has already ended. Renew it instead of extending it.");
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- renew

    /**
     * Creates the renewal agreement (start = old end + 1 day). {@code newSecurityDeposit} is the deposit required by the new
     * lease; with {@code carryDeposit} any balance held on the old agreement moves to the new one.
     */
    public RentalAgreement renew(long agreementId, int durationMonths, BigDecimal newMonthlyRent, BigDecimal newSecurityDeposit,
                                 boolean carryDeposit, String note) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        ensureSupported();
        checkMonths(durationMonths, "Lease length");
        BigDecimal rent = checkMoney(newMonthlyRent, true, "Monthly rent", "monthlyRent");
        BigDecimal deposit = newSecurityDeposit == null ? null : checkMoney(newSecurityDeposit, false, "Security deposit", "securityDeposit");
        String why = checkNote(note);
        AppUser actor = auth.requireAuthenticated();
        return tx.inTransaction(() -> {
            RentalAgreement old = load(agreementId);
            Property property = propertyDao.findByIdForUpdate(old.getPropertyId())
                    .orElseThrow(() -> new EntityNotFoundException("The property no longer exists."));
            Optional<String> blocker = renewalBlocker(old);
            if (blocker.isPresent()) {
                throw new BusinessRuleException(blocker.get(), "LR-01");
            }
            if (!property.isActive()) {
                throw new BusinessRuleException("Property " + property.getName() + " is inactive and cannot be rented.", "BR-05");
            }
            Tenant tenant = tenantDao.findById(old.getTenantId())
                    .orElseThrow(() -> new EntityNotFoundException("The tenant no longer exists."));
            if (!tenant.isActive()) {
                throw new BusinessRuleException("Tenant " + tenant.getFullName() + " is inactive and cannot sign a new agreement.", "BR-05");
            }
            BigDecimal required = deposit == null ? old.getSecurityDeposit() : deposit;
            LocalDate newStart = old.getEffectiveEndDate().plusDays(1);
            LocalDate newEnd = RentScheduleGenerator.endDate(newStart, durationMonths);
            List<RentalAgreement> clashes = agreementDao.findOverlapping(old.getPropertyId(), newStart, newEnd);
            if (!clashes.isEmpty()) {
                RentalAgreement c = clashes.get(0);
                throw new BusinessRuleException("Property " + property.getName() + " already has an agreement from " + c.getStartDate()
                        + " to " + c.getEffectiveEndDate() + ", which overlaps the renewal period " + newStart + " to " + newEnd + ".", "BR-13");
            }
            Optional<Long> oldDepositId = carryDeposit ? depositDao.findDepositId(agreementId) : Optional.empty();
            BigDecimal carried = BigDecimal.ZERO;
            if (oldDepositId.isPresent()) {
                depositDao.lockDeposit(oldDepositId.get());
                carried = depositDao.totals(oldDepositId.get()).balance();
                if (carried.signum() > 0 && required.signum() > 0 && carried.compareTo(required) > 0) {
                    throw new BusinessRuleException("The deposit held (" + MoneyUtil.format(carried) + ") is more than the new lease requires ("
                            + MoneyUtil.format(required) + "). Refund the difference first, or raise the required deposit.", "LR-05");
                }
            }

            RentalAgreement next = new RentalAgreement();
            next.setPropertyId(old.getPropertyId());
            next.setTenantId(old.getTenantId());
            next.setStartDate(newStart);
            next.setDurationMonths(durationMonths);
            next.setEndDate(newEnd);
            next.setMonthlyRent(rent);
            next.setSecurityDeposit(required);
            next.setStatus(AgreementStatus.ACTIVE);
            next.setNotes(old.getNotes());
            RentalAgreement inserted = agreementDao.insert(next);
            chargeDao.insertAll(RentScheduleGenerator.generate(inserted.getAgreementId(), newStart, durationMonths, rent));
            try {
                leaseDao.setRenewedFrom(inserted.getAgreementId(), agreementId);
            } catch (DuplicateKeyException already) {
                throw new BusinessRuleException("This agreement has already been renewed.", "LR-02");
            }

            if (carried.signum() > 0) {
                LocalDate today = LocalDate.now(clock);
                long outId = depositDao.insertTransaction(new DepositTransaction(null, oldDepositId.get(), DepositType.TRANSFER_OUT, carried,
                        today, null, null, null, "Carried over to agreement " + inserted.getAgreementId() + " on renewal",
                        inserted.getAgreementId(), null, null, null));
                long newDepositId = depositDao.insertDeposit(inserted.getAgreementId());
                long inId = depositDao.insertTransaction(new DepositTransaction(null, newDepositId, DepositType.TRANSFER_IN, carried,
                        today, null, null, null, "Carried over from agreement " + agreementId + " on renewal", agreementId, null, null, null));
                audit.record("DEPOSIT", oldDepositId.get(), "TRANSFERRED_OUT", null, AuditService.details("txnId", outId,
                        "amount", carried.toPlainString(), "toAgreementId", inserted.getAgreementId()));
                audit.record("DEPOSIT", newDepositId, "TRANSFERRED_IN", null, AuditService.details("txnId", inId,
                        "amount", carried.toPlainString(), "fromAgreementId", agreementId));
            }
            leaseDao.insertChange(new LeaseChange(null, inserted.getAgreementId(), "RENEWED", agreementId, old.getStartDate(),
                    old.getEffectiveEndDate(), old.getMonthlyRent(), newStart, newEnd, rent, actor.id(), actor.username(), why, null));
            audit.record("AGREEMENT", inserted.getAgreementId(), "RENEWED",
                    AuditService.details("renewedAgreementId", agreementId, "startDate", old.getStartDate(), "endDate",
                            old.getEffectiveEndDate(), "monthlyRent", old.getMonthlyRent()),
                    AuditService.details("startDate", newStart, "endDate", newEnd, "monthlyRent", rent, "securityDeposit", required,
                            "depositCarriedOver", carried.toPlainString()));
            return inserted;
        });
    }

    // ---------------------------------------------------------------- extend

    /** Adds months to an agreement that has not ended, at the current rent. */
    public RentalAgreement extend(long agreementId, int additionalMonths, String note) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        ensureSupported();
        checkMonths(additionalMonths, "Months to add");
        String why = checkNote(note);
        AppUser actor = auth.requireAuthenticated();
        return tx.inTransaction(() -> {
            RentalAgreement a = load(agreementId);
            Property property = propertyDao.findByIdForUpdate(a.getPropertyId())
                    .orElseThrow(() -> new EntityNotFoundException("The property no longer exists."));
            Optional<String> blocker = extensionBlocker(a);
            if (blocker.isPresent()) {
                throw new BusinessRuleException(blocker.get(), "LR-01");
            }
            int newDuration = a.getDurationMonths() + additionalMonths;
            if (newDuration > MAX_MONTHS) {
                throw new BusinessRuleException("The total lease length cannot exceed " + MAX_MONTHS + " months (it is "
                        + a.getDurationMonths() + " now).", "LR-07");
            }
            LocalDate oldEnd = a.getEffectiveEndDate();
            LocalDate newEnd = RentScheduleGenerator.endDate(a.getStartDate(), newDuration);
            List<RentalAgreement> clashes = agreementDao.findOverlapping(a.getPropertyId(), oldEnd.plusDays(1), newEnd);
            if (!clashes.isEmpty()) {
                RentalAgreement c = clashes.get(0);
                throw new BusinessRuleException("Property " + property.getName() + " already has an agreement from " + c.getStartDate()
                        + " to " + c.getEffectiveEndDate() + ", which overlaps the extension up to " + newEnd + ".", "BR-13");
            }
            List<RentCharge> schedule = RentScheduleGenerator.generate(agreementId, a.getStartDate(), newDuration, a.getMonthlyRent());
            chargeDao.insertAll(schedule.subList(a.getDurationMonths(), newDuration));
            if (leaseDao.extendTerm(agreementId, newDuration, newEnd) == 0) {
                throw new BusinessRuleException("Terminated agreements cannot be renewed or extended.", "LR-01");
            }
            leaseDao.insertChange(new LeaseChange(null, agreementId, "EXTENDED", null, a.getStartDate(), oldEnd, a.getMonthlyRent(),
                    a.getStartDate(), newEnd, a.getMonthlyRent(), actor.id(), actor.username(), why, null));
            audit.record("AGREEMENT", agreementId, "EXTENDED",
                    AuditService.details("endDate", oldEnd, "durationMonths", a.getDurationMonths()),
                    AuditService.details("endDate", newEnd, "durationMonths", newDuration, "monthlyRent", a.getMonthlyRent()));
            return load(agreementId);
        });
    }

    // ---------------------------------------------------------------- reads

    /** Extensions of this agreement and renewals that replaced it or were created from it, oldest first. */
    public List<LeaseChange> history(long agreementId) {
        auth.require(Permission.VIEW_DATA);
        return leaseDao.supportsRenewal() ? leaseDao.findChanges(agreementId) : List.of();
    }

    /** Leases ending within the next {@code days} days (1 to 365), soonest first; renewed leases are not listed. */
    public List<ExpiringLeaseRow> endingWithin(int days) throws ValidationException {
        auth.require(Permission.VIEW_DATA);
        if (days < 1 || days > 365) {
            throw new ValidationException("Choose a period between 1 and 365 days.", "days");
        }
        LocalDate today = LocalDate.now(clock);
        return agreementDao.findEndingBetween(today, today.plusDays(days));
    }

    /** Leases ending in the configured warning window (0 to 3650 days). */
    public List<ExpiringLeaseRow> endingWithinWarningWindow(int days) throws ValidationException {
        auth.require(Permission.VIEW_DATA);
        if (days < 0 || days > 3650) {
            throw new ValidationException("Configured lease warning must be between 0 and 3650 days.", "days");
        }
        LocalDate today = LocalDate.now(clock);
        return agreementDao.findEndingBetween(today, today.plusDays(days));
    }

    /** Ended, un-renewed leases that still have rent owing or a deposit held. */
    public List<LeaseAttentionRow> needingAttention() {
        auth.require(Permission.VIEW_DATA);
        return leaseDao.findEndedNeedingAttention(LocalDate.now(clock));
    }

    // ---------------------------------------------------------------- helpers

    private void ensureSupported() throws ConfigurationException {
        if (!leaseDao.supportsRenewal() || !depositDao.isAvailable()) {
            throw new ConfigurationException("Lease renewal needs db/migrations/v1.5_deposits_leases.sql applied (see README).");
        }
    }

    private RentalAgreement load(long agreementId) throws EntityNotFoundException {
        return agreementDao.findById(agreementId).orElseThrow(() -> new EntityNotFoundException("That agreement no longer exists."));
    }

    private static void checkMonths(int months, String label) throws ValidationException {
        if (months < 1 || months > MAX_MONTHS) {
            throw new ValidationException(label + " must be between 1 and " + MAX_MONTHS + " months.", "months");
        }
    }

    private static BigDecimal checkMoney(BigDecimal value, boolean positive, String label, String field) throws ValidationException {
        if (value == null || (positive ? value.signum() <= 0 : value.signum() < 0)) {
            throw new ValidationException(label + (positive ? " must be greater than zero." : " cannot be negative."), field);
        }
        if (!MoneyUtil.hasValidScale(value)) {
            throw new ValidationException(label + " can have at most 2 decimal places.", field);
        }
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException(label + " is too large.", field);
        }
        return value.setScale(2);
    }

    private static String checkNote(String note) throws ValidationException {
        if (note == null || note.isBlank()) {
            return null;
        }
        String n = note.trim();
        if (n.length() > 255) {
            throw new ValidationException("The note can be at most 255 characters.", "note");
        }
        return n;
    }
}
