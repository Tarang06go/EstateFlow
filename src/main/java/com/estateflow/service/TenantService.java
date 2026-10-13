package com.estateflow.service;

import com.estateflow.dao.AgreementDao;
import com.estateflow.dao.TenantDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.exception.*;
import com.estateflow.model.Tenant;
import com.estateflow.service.rules.Validators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Tenant workflows: BR-01, BR-03. */
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantDao tenantDao;
    private final AgreementDao agreementDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;

    public TenantService(TenantDao tenantDao, AgreementDao agreementDao, TransactionManager tx, AuditService audit,
                         Clock clock) {
        this.tenantDao = tenantDao;
        this.agreementDao = agreementDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
    }

    public Tenant create(Tenant tenant) throws EstateFlowException {
        prepare(tenant);
        ensureUnique(tenant);
        try {
            Tenant saved = tenantDao.insert(tenant);
            log.info("Tenant {} created", saved.getId());
            return saved;
        } catch (DuplicateKeyException e) {
            throw duplicate(tenant, e);
        }
    }

    public Tenant update(Tenant tenant) throws EstateFlowException {
        Tenant existing = get(tenant.getId());
        prepare(tenant);
        ensureUnique(tenant);
        tenant.setActive(existing.isActive());
        try {
            requireUpdated(tenantDao.update(tenant));
        } catch (DuplicateKeyException e) {
            throw duplicate(tenant, e);
        }
        log.info("Tenant {} updated", tenant.getId());
        return tenant;
    }

    /** BR-03: a tenant with an open agreement (effective_end_date >= today) cannot be deactivated. */
    public void deactivate(Long tenantId) throws EstateFlowException {
        Tenant tenant = get(tenantId);
        if (!tenant.isActive()) {
            return;
        }
        if (agreementDao.existsOpenForTenant(tenantId, LocalDate.now(clock))) {
            throw new BusinessRuleException("Tenant " + tenant.getFullName()
                    + " has an open rental agreement and cannot be deactivated.", "BR-03");
        }
        tenant.setActive(false);
        tx.runInTransaction(() -> {
            requireUpdated(tenantDao.update(tenant));
            audit.record("TENANT", tenantId, "DEACTIVATED", AuditService.details("active", true), AuditService.details("active", false));
        });
        log.info("Tenant {} deactivated", tenantId);
    }

    public void reactivate(Long tenantId) throws EstateFlowException {
        Tenant tenant = get(tenantId);
        if (tenant.isActive()) {
            return;
        }
        tenant.setActive(true);
        tx.runInTransaction(() -> {
            requireUpdated(tenantDao.update(tenant));
            audit.record("TENANT", tenantId, "REACTIVATED", AuditService.details("active", false), AuditService.details("active", true));
        });
        log.info("Tenant {} reactivated", tenantId);
    }

    public Tenant get(Long tenantId) throws EntityNotFoundException {
        if (tenantId == null) {
            throw new EntityNotFoundException("Tenant not found.");
        }
        return tenantDao.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant " + tenantId + " no longer exists."));
    }

    public List<Tenant> findAll() {
        return tenantDao.findAll();
    }

    public List<Tenant> search(String text) {
        return text == null || text.isBlank() ? tenantDao.findAll() : tenantDao.search(text.trim());
    }

    private void prepare(Tenant tenant) throws ValidationException {
        if (tenant == null) {
            throw new ValidationException("Tenant details are required.", null);
        }
        tenant.setPhone(Validators.normalizePhone(tenant.getPhone()));
        tenant.setIdProofType(blankToNull(tenant.getIdProofType()));
        tenant.setIdProofNumber(blankToNull(tenant.getIdProofNumber()));
        tenant.setEmergencyContactPhone(blankToNull(tenant.getEmergencyContactPhone() == null ? null
                : Validators.normalizePhone(tenant.getEmergencyContactPhone())));
        Validators.validateContact(tenant);
    }

    private void ensureUnique(Tenant tenant) throws DuplicateEntityException {
        Optional<Tenant> byPhone = tenantDao.findByPhone(tenant.getPhone());
        if (byPhone.isPresent() && !byPhone.get().getId().equals(tenant.getId())) {
            throw new DuplicateEntityException("Another tenant already uses phone number " + tenant.getPhone() + ".");
        }
        if (tenant.getIdProofType() != null) {
            Optional<Tenant> byProof = tenantDao.findByIdProof(tenant.getIdProofType(), tenant.getIdProofNumber());
            if (byProof.isPresent() && !byProof.get().getId().equals(tenant.getId())) {
                throw new DuplicateEntityException("Another tenant is already registered with "
                        + tenant.getIdProofType() + " " + tenant.getIdProofNumber() + ".");
            }
        }
    }

    private DuplicateEntityException duplicate(Tenant tenant, DuplicateKeyException cause) {
        String key = cause.getConstraintName() == null ? "" : cause.getConstraintName();
        String what = key.contains("id_proof")
                ? "ID proof " + tenant.getIdProofType() + " " + tenant.getIdProofNumber()
                : "phone number " + tenant.getPhone();
        return new DuplicateEntityException("Another tenant is already registered with " + what + ".", cause);
    }

    private void requireUpdated(int rows) throws EntityNotFoundException {
        if (rows == 0) {
            throw new EntityNotFoundException("The tenant record no longer exists or was changed. Refresh and try again.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
