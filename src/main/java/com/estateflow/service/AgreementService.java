package com.estateflow.service;

import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.dao.*;
import com.estateflow.dto.AgreementListRow;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.exception.*;
import com.estateflow.model.Property;
import com.estateflow.model.RentCharge;
import com.estateflow.model.RentalAgreement;
import com.estateflow.model.Tenant;
import com.estateflow.model.enums.AgreementFilter;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.model.enums.EffectiveAgreementStatus;
import com.estateflow.service.rules.RentScheduleGenerator;
import com.estateflow.service.rules.Validators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Agreement workflows: BR-10 to BR-17. */
public class AgreementService {

    static final int ROW_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(AgreementService.class);

    private final AgreementDao agreementDao;
    private final RentChargeDao chargeDao;
    private final PaymentDao paymentDao;
    private final PropertyDao propertyDao;
    private final TenantDao tenantDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;
    private final int warningDays;

    private final Authorizer auth;

    public AgreementService(AgreementDao agreementDao, RentChargeDao chargeDao, PaymentDao paymentDao,
                            PropertyDao propertyDao, TenantDao tenantDao, TransactionManager tx,
                            AuditService audit, Clock clock, int warningDays,
            Authorizer auth) {
        this.auth = auth;
        this.agreementDao = agreementDao;
        this.chargeDao = chargeDao;
        this.paymentDao = paymentDao;
        this.propertyDao = propertyDao;
        this.tenantDao = tenantDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.warningDays = warningDays;
    }

    /**
     * BR-10 to BR-14. Inside one transaction: lock the property (first data access), check overlap,
     * insert the agreement, and insert one rent charge per month.
     */
    public RentalAgreement create(Long propertyId, Long tenantId, LocalDate startDate, int durationMonths,
                                  BigDecimal monthlyRent, BigDecimal securityDeposit, String notes)
            throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        BigDecimal deposit = securityDeposit == null ? BigDecimal.ZERO : securityDeposit;
        validateCreate(propertyId, tenantId, startDate, durationMonths, monthlyRent, deposit, notes);
        LocalDate endDate = RentScheduleGenerator.endDate(startDate, durationMonths);

        RentalAgreement saved = tx.inTransaction(() -> {
            Property property = propertyDao.findByIdForUpdate(propertyId)
                    .orElseThrow(() -> new EntityNotFoundException("The selected property no longer exists."));
            if (!property.isActive()) {
                throw new BusinessRuleException("Property " + property.getName()
                        + " is inactive and cannot be rented.", "BR-05");
            }
            Tenant tenant = tenantDao.findById(tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("The selected tenant no longer exists."));
            if (!tenant.isActive()) {
                throw new BusinessRuleException("Tenant " + tenant.getFullName()
                        + " is inactive and cannot sign a new agreement.", "BR-05");
            }
            List<RentalAgreement> clashes = agreementDao.findOverlapping(propertyId, startDate, endDate);
            if (!clashes.isEmpty()) {
                RentalAgreement c = clashes.get(0);
                throw new BusinessRuleException("Property " + property.getName() + " already has an agreement from "
                        + c.getStartDate() + " to " + c.getEffectiveEndDate()
                        + ", which overlaps " + startDate + " to " + endDate + ".", "BR-13");
            }
            RentalAgreement agreement = new RentalAgreement();
            agreement.setPropertyId(propertyId);
            agreement.setTenantId(tenantId);
            agreement.setStartDate(startDate);
            agreement.setDurationMonths(durationMonths);
            agreement.setEndDate(endDate);
            agreement.setMonthlyRent(monthlyRent);
            agreement.setSecurityDeposit(deposit);
            agreement.setStatus(AgreementStatus.ACTIVE);
            agreement.setNotes(blankToNull(notes));
            RentalAgreement inserted = agreementDao.insert(agreement);
            chargeDao.insertAll(RentScheduleGenerator.generate(inserted.getAgreementId(), startDate,
                    durationMonths, monthlyRent));
            audit.record("AGREEMENT", inserted.getAgreementId(), "CREATED", null, AuditService.details(
                    "propertyId", propertyId, "tenantId", tenantId, "startDate", startDate, "endDate", endDate,
                    "durationMonths", durationMonths, "monthlyRent", monthlyRent, "securityDeposit", deposit));
            return inserted;
        });
        log.info("Agreement {} created for property {} with {} rent charges",
                saved.getAgreementId(), propertyId, durationMonths);
        return saved;
    }

    /**
     * BR-15: terminate an active, unexpired agreement; cancel live charges that start after the
     * termination date. Rejected if a valid payment exists on any such charge.
     */
    public RentalAgreement terminate(Long agreementId, LocalDate terminationDate) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        if (agreementId == null) {
            throw new ValidationException("Select an agreement.", "agreement");
        }
        if (terminationDate == null) {
            throw new ValidationException("Termination date is required.", "terminationDate");
        }
        RentalAgreement result = tx.inTransaction(() -> {
            RentalAgreement a = agreementDao.findById(agreementId)
                    .orElseThrow(() -> new EntityNotFoundException("Agreement " + agreementId + " no longer exists."));
            LocalDate today = LocalDate.now(clock);
            if (a.getStatus() != AgreementStatus.ACTIVE) {
                throw new BusinessRuleException("Agreement " + agreementId + " is already terminated.", "BR-15");
            }
            if (today.isAfter(a.getEndDate())) {
                throw new BusinessRuleException("Agreement " + agreementId
                        + " has already expired and cannot be terminated.", "BR-15");
            }
            if (terminationDate.isBefore(a.getStartDate()) || !terminationDate.isBefore(a.getEndDate())) {
                throw new BusinessRuleException("The termination date must be between " + a.getStartDate()
                        + " and the day before the agreement ends (" + a.getEndDate().minusDays(1) + ").", "BR-15");
            }
            if (paymentDao.existsValidForChargesAfter(agreementId, terminationDate)) {
                throw new BusinessRuleException("Valid payments exist for rent periods starting after "
                        + terminationDate + ". Void them or choose a later termination date.", "BR-15");
            }
            a.setStatus(AgreementStatus.TERMINATED);
            a.setTerminationDate(terminationDate);
            if (agreementDao.update(a) == 0) {
                throw new EntityNotFoundException("The agreement was changed by someone else. Refresh and try again.");
            }
            chargeDao.cancelAfter(agreementId, terminationDate, nowUtc());
            audit.record("AGREEMENT", agreementId, "TERMINATED", AuditService.details("status", "ACTIVE"),
                    AuditService.details("status", "TERMINATED", "terminationDate", terminationDate));
            return a;
        });
        log.info("Agreement {} terminated effective {}", agreementId, terminationDate);
        return result;
    }

    /** BR-16: only the notes of an agreement can be edited. */
    public RentalAgreement updateNotes(Long agreementId, String notes) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        if (notes != null && notes.length() > 500) {
            throw new ValidationException("Notes cannot exceed 500 characters.", "notes");
        }
        RentalAgreement a = get(agreementId);
        a.setNotes(blankToNull(notes));
        tx.runInTransaction(() -> {
            if (agreementDao.update(a) == 0) {
                throw new EntityNotFoundException("The agreement was changed by someone else. Refresh and try again.");
            }
            audit.record("AGREEMENT", agreementId, "NOTES_UPDATED", null, AuditService.details("notesLength",
                    a.getNotes() == null ? 0 : a.getNotes().length())); // content is not logged: notes may hold personal details
        });
        return a;
    }

    public RentalAgreement get(Long agreementId) throws EntityNotFoundException {
        auth.require(Permission.VIEW_DATA);
        if (agreementId == null) {
            throw new EntityNotFoundException("Agreement not found.");
        }
        return agreementDao.findById(agreementId)
                .orElseThrow(() -> new EntityNotFoundException("Agreement " + agreementId + " no longer exists."));
    }

    public EffectiveAgreementStatus effectiveStatus(RentalAgreement agreement) {
        return agreement.effectiveStatus(LocalDate.now(clock), warningDays);
    }

    public List<AgreementListRow> findAllRows() {
        auth.require(Permission.VIEW_DATA);
        return agreementDao.findAllRows();
    }

    public List<RentalAgreement> findByProperty(Long propertyId) {
        auth.require(Permission.VIEW_DATA);
        return agreementDao.findByProperty(propertyId);
    }

    /** Agreements whose effective period ends within the configured warning window (BR-27). */
    public List<ExpiringLeaseRow> findEndingSoon() {
        auth.require(Permission.VIEW_DATA);
        LocalDate today = LocalDate.now(clock);
        return agreementDao.findEndingBetween(today, today.plusDays(warningDays));
    }

    /** Filtered, capped agreement rows for the agreements screen. */
    public List<AgreementListRow> searchRows(String text, AgreementFilter filter) {
        auth.require(Permission.VIEW_DATA);
        return agreementDao.searchRows(text, filter, LocalDate.now(clock), ROW_LIMIT);
    }

    public int getWarningDays() {
        return warningDays;
    }

    private void validateCreate(Long propertyId, Long tenantId, LocalDate startDate, int months,
                                BigDecimal rent, BigDecimal deposit, String notes) throws ValidationException {
        if (propertyId == null) {
            throw new ValidationException("Select a property.", "property");
        }
        if (tenantId == null) {
            throw new ValidationException("Select a tenant.", "tenant");
        }
        if (startDate == null) {
            throw new ValidationException("Start date is required.", "startDate");
        }
        if (months < 1 || months > 120) {
            throw new ValidationException("Duration must be between 1 and 120 months.", "durationMonths");
        }
        Validators.validatePositiveMoney(rent, "monthlyRent", "Monthly rent must be greater than zero");
        Validators.validateNonNegativeMoney(deposit, "securityDeposit", "Security deposit cannot be negative");
        if (notes != null && notes.length() > 500) {
            throw new ValidationException("Notes cannot exceed 500 characters.", "notes");
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
