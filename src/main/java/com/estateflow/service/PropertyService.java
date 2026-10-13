package com.estateflow.service;

import com.estateflow.dao.AgreementDao;
import com.estateflow.dao.OwnerDao;
import com.estateflow.dao.PropertyDao;
import com.estateflow.dao.ReportingDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.dto.AgreementListRow;
import com.estateflow.dto.MaintenanceReportRow;
import com.estateflow.dto.PropertyDetails;
import com.estateflow.dto.PropertyOccupancyRow;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.exception.*;
import com.estateflow.model.Owner;
import com.estateflow.model.Property;
import com.estateflow.service.rules.Validators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Property workflows: BR-04, BR-05. */
public class PropertyService {

    /** Upper bound of rows loaded for the property list. */
    static final int ROW_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);

    private final PropertyDao propertyDao;
    private final OwnerDao ownerDao;
    private final AgreementDao agreementDao;
    private final ReportingDao reportingDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;

    public PropertyService(PropertyDao propertyDao, OwnerDao ownerDao, AgreementDao agreementDao,
                           ReportingDao reportingDao, TransactionManager tx, AuditService audit, Clock clock) {
        this.propertyDao = propertyDao;
        this.ownerDao = ownerDao;
        this.agreementDao = agreementDao;
        this.reportingDao = reportingDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
    }

    /** BR-05: only active owners can receive new properties. */
    public Property create(Property property) throws EstateFlowException {
        validate(property);
        requireActiveOwner(property.getOwnerId());
        Property saved = propertyDao.insert(property);
        log.info("Property {} created for owner {}", saved.getPropertyId(), saved.getOwnerId());
        return saved;
    }

    public Property update(Property property) throws EstateFlowException {
        Property existing = get(property.getPropertyId());
        validate(property);
        if (!existing.getOwnerId().equals(property.getOwnerId())) {
            requireActiveOwner(property.getOwnerId());
        }
        property.setActive(existing.isActive());
        if (propertyDao.update(property) == 0) {
            throw new EntityNotFoundException("The property record no longer exists or was changed. Refresh and try again.");
        }
        log.info("Property {} updated", property.getPropertyId());
        return property;
    }

    /** BR-04: a property with an open agreement cannot be deactivated. */
    public void deactivate(Long propertyId) throws EstateFlowException {
        Property property = get(propertyId);
        if (!property.isActive()) {
            return;
        }
        if (agreementDao.existsOpenForProperty(propertyId, LocalDate.now(clock))) {
            throw new BusinessRuleException("Property " + property.getName()
                    + " has an open rental agreement and cannot be deactivated.", "BR-04");
        }
        property.setActive(false);
        tx.runInTransaction(() -> {
            save(property);
            audit.record("PROPERTY", propertyId, "DEACTIVATED", AuditService.details("active", true), AuditService.details("active", false));
        });
        log.info("Property {} deactivated", propertyId);
    }

    /** BR-04: reactivation requires an active owner. */
    public void reactivate(Long propertyId) throws EstateFlowException {
        Property property = get(propertyId);
        if (property.isActive()) {
            return;
        }
        Owner owner = ownerDao.findById(property.getOwnerId())
                .orElseThrow(() -> new EntityNotFoundException("The owner of this property no longer exists."));
        if (!owner.isActive()) {
            throw new BusinessRuleException("Owner " + owner.getFullName()
                    + " is inactive. Reactivate the owner before reactivating the property.", "BR-04");
        }
        property.setActive(true);
        tx.runInTransaction(() -> {
            save(property);
            audit.record("PROPERTY", propertyId, "REACTIVATED", AuditService.details("active", false), AuditService.details("active", true));
        });
        log.info("Property {} reactivated", propertyId);
    }

    public Property get(Long propertyId) throws EntityNotFoundException {
        if (propertyId == null) {
            throw new EntityNotFoundException("Property not found.");
        }
        return propertyDao.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException("Property " + propertyId + " no longer exists."));
    }

    public List<Property> findAll() {
        return propertyDao.findAll();
    }

    /** List rows with owner name and occupancy derived for today (BR-17). */
    public List<PropertyOccupancyRow> findRows(String text) {
        return reportingDao.searchPropertyRows(text == null ? "" : text.trim(), LocalDate.now(clock), ROW_LIMIT);
    }

    /** Property, owner, current agreement, histories and totals for the detail screen. */
    public PropertyDetails details(Long propertyId) throws EstateFlowException {
        Property property = get(propertyId);
        Owner owner = ownerDao.findById(property.getOwnerId()).orElse(null);
        LocalDate today = LocalDate.now(clock);
        List<AgreementListRow> agreements = agreementDao.findRowsByProperty(propertyId);
        AgreementListRow current = agreements.stream()
                .filter(a -> !a.startDate().isAfter(today) && !a.effectiveEndDate().isBefore(today))
                .findFirst().orElse(null);
        List<MaintenanceReportRow> maintenance = reportingDao.findMaintenanceRowsForProperty(propertyId);
        BigDecimal cost = maintenance.stream()
                .filter(m -> m.cost() != null && (m.status() == MaintenanceStatus.IN_PROGRESS || m.status() == MaintenanceStatus.RESOLVED))
                .map(MaintenanceReportRow::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PropertyDetails(property, owner == null ? "" : owner.getFullName(), owner == null ? "" : owner.getPhone(),
                current, agreements, reportingDao.findPaymentRowsForProperty(propertyId), maintenance,
                reportingDao.outstandingForProperty(propertyId, today), cost);
    }

    private void save(Property property) throws EntityNotFoundException {
        if (propertyDao.update(property) == 0) {
            throw new EntityNotFoundException("The property record no longer exists or was changed. Refresh and try again.");
        }
    }

    private void requireActiveOwner(Long ownerId) throws EstateFlowException {
        Owner owner = ownerDao.findById(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("The selected owner no longer exists."));
        if (!owner.isActive()) {
            throw new BusinessRuleException("Owner " + owner.getFullName()
                    + " is inactive and cannot receive new properties.", "BR-05");
        }
    }

    private void validate(Property p) throws ValidationException {
        if (p == null) {
            throw new ValidationException("Property details are required.", null);
        }
        if (p.getOwnerId() == null) {
            throw new ValidationException("Select an owner.", "owner");
        }
        Validators.requireNonBlank(p.getName(), "name", "Property name is required");
        Validators.requireNonBlank(p.getAddressLine(), "addressLine", "Address is required");
        Validators.requireNonBlank(p.getCity(), "city", "City is required");
        if (p.getPropertyType() == null) {
            throw new ValidationException("Select a property type.", "propertyType");
        }
        Validators.validateNonNegativeMoney(p.getListedRent(), "listedRent", "Listed rent is required and cannot be negative");
        if (p.getBedrooms() != null && (p.getBedrooms() < 0 || p.getBedrooms() > 255)) {
            throw new ValidationException("Bedrooms must be between 0 and 255.", "bedrooms");
        }
        BigDecimal area = p.getAreaSqft();
        if (area != null && (area.signum() <= 0 || area.scale() > 2)) {
            throw new ValidationException("Area must be positive with at most 2 decimal places.", "areaSqft");
        }
    }
}
