package com.estateflow.service;

import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.dao.OwnerDao;
import com.estateflow.dao.PropertyDao;
import com.estateflow.dao.ReportingDao;
import com.estateflow.dto.OwnerDetails;
import com.estateflow.dao.TransactionManager;
import com.estateflow.exception.*;
import com.estateflow.model.Owner;
import com.estateflow.service.rules.Validators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Owner workflows: BR-01, BR-02. */
public class OwnerService {

    private static final Logger log = LoggerFactory.getLogger(OwnerService.class);

    private final OwnerDao ownerDao;
    private final PropertyDao propertyDao;
    private final ReportingDao reportingDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;

    private final Authorizer auth;

    public OwnerService(OwnerDao ownerDao, PropertyDao propertyDao, ReportingDao reportingDao, TransactionManager tx,
                        AuditService audit, Clock clock,
            Authorizer auth) {
        this.auth = auth;
        this.ownerDao = ownerDao;
        this.propertyDao = propertyDao;
        this.reportingDao = reportingDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
    }

    /** Owner profile: properties with derived occupancy and the rent currently outstanding on them. */
    public OwnerDetails details(Long ownerId) throws EntityNotFoundException {
        auth.require(Permission.VIEW_DATA);
        Owner owner = get(ownerId);
        LocalDate today = LocalDate.now(clock);
        return new OwnerDetails(owner, reportingDao.findPropertyRowsForOwner(ownerId, today),
                reportingDao.outstandingForOwner(ownerId, today));
    }

    /** BR-01: validates, normalizes the phone, and enforces uniqueness. */
    public Owner create(Owner owner) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        prepare(owner);
        ensurePhoneFree(owner);
        try {
            Owner saved = tx.inTransaction(() -> {
                Owner inserted = ownerDao.insert(owner);
                audit.record("OWNER", inserted.getId(), "CREATED", null, AuditService.details("name", inserted.getFullName()));
                return inserted;
            });
            log.info("Owner {} created", saved.getId());
            return saved;
        } catch (DuplicateKeyException e) {
            throw duplicate(owner, e);
        }
    }

    public Owner update(Owner owner) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        Owner existing = get(owner.getId());
        prepare(owner);
        ensurePhoneFree(owner);
        owner.setActive(existing.isActive());
        try {
            tx.runInTransaction(() -> {
                requireUpdated(ownerDao.update(owner));
                audit.record("OWNER", owner.getId(), "UPDATED", AuditService.details("name", existing.getFullName()),
                        AuditService.details("name", owner.getFullName()));
            });
        } catch (DuplicateKeyException e) {
            throw duplicate(owner, e);
        }
        log.info("Owner {} updated", owner.getId());
        return owner;
    }

    /** BR-02: an owner with active properties cannot be deactivated. */
    public void deactivate(Long ownerId) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        Owner owner = get(ownerId);
        if (!owner.isActive()) {
            return;
        }
        int active = propertyDao.countActiveByOwner(ownerId);
        if (active > 0) {
            throw new BusinessRuleException("Owner " + owner.getFullName() + " still owns " + active
                    + " active propert" + (active == 1 ? "y" : "ies")
                    + ". Deactivate those properties first.", "BR-02");
        }
        owner.setActive(false);
        tx.runInTransaction(() -> {
            requireUpdated(ownerDao.update(owner));
            audit.record("OWNER", ownerId, "DEACTIVATED", AuditService.details("active", true), AuditService.details("active", false));
        });
        log.info("Owner {} deactivated", ownerId);
    }

    /** BR-02: an inactive owner may be reactivated at any time. */
    public void reactivate(Long ownerId) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        Owner owner = get(ownerId);
        if (owner.isActive()) {
            return;
        }
        owner.setActive(true);
        tx.runInTransaction(() -> {
            requireUpdated(ownerDao.update(owner));
            audit.record("OWNER", ownerId, "REACTIVATED", AuditService.details("active", false), AuditService.details("active", true));
        });
        log.info("Owner {} reactivated", ownerId);
    }

    public Owner get(Long ownerId) throws EntityNotFoundException {
        auth.require(Permission.VIEW_DATA);
        if (ownerId == null) {
            throw new EntityNotFoundException("Owner not found.");
        }
        return ownerDao.findById(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Owner " + ownerId + " no longer exists."));
    }

    public List<Owner> findAll() {
        auth.require(Permission.VIEW_DATA);
        return ownerDao.findAll();
    }

    public List<Owner> search(String text) {
        auth.require(Permission.VIEW_DATA);
        return text == null || text.isBlank() ? ownerDao.findAll() : ownerDao.search(text.trim());
    }

    private void prepare(Owner owner) throws ValidationException {
        if (owner == null) {
            throw new ValidationException("Owner details are required.", null);
        }
        owner.setPhone(Validators.normalizePhone(owner.getPhone()));
        Validators.validateContact(owner);
    }

    private void ensurePhoneFree(Owner owner) throws DuplicateEntityException {
        Optional<Owner> other = ownerDao.findByPhone(owner.getPhone());
        if (other.isPresent() && !other.get().getId().equals(owner.getId())) {
            throw new DuplicateEntityException("Another owner already uses phone number " + owner.getPhone() + ".");
        }
    }

    private DuplicateEntityException duplicate(Owner owner, DuplicateKeyException cause) {
        return new DuplicateEntityException("Another owner already uses phone number " + owner.getPhone() + ".", cause);
    }

    private void requireUpdated(int rows) throws EntityNotFoundException {
        if (rows == 0) {
            throw new EntityNotFoundException("The owner record no longer exists or was changed. Refresh and try again.");
        }
    }
}
