package com.estateflow.service;

import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.dao.AgreementDao;
import com.estateflow.dao.MaintenanceDao;
import com.estateflow.dao.PropertyDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.dao.ReportingDao;
import com.estateflow.dto.MaintenanceReportRow;
import com.estateflow.dto.MaintenanceSummary;
import com.estateflow.dto.MonthlyAmount;
import com.estateflow.exception.*;
import com.estateflow.model.MaintenanceRequest;
import com.estateflow.model.Property;
import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.service.rules.Validators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Maintenance workflows: BR-30 to BR-32. */
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final MaintenanceDao maintenanceDao;
    private final PropertyDao propertyDao;
    private final AgreementDao agreementDao;
    private final ReportingDao reportingDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;

    private final Authorizer auth;

    public MaintenanceService(MaintenanceDao maintenanceDao, PropertyDao propertyDao, AgreementDao agreementDao,
                              ReportingDao reportingDao, TransactionManager tx, AuditService audit, Clock clock,
            Authorizer auth) {
        this.auth = auth;
        this.maintenanceDao = maintenanceDao;
        this.propertyDao = propertyDao;
        this.agreementDao = agreementDao;
        this.reportingDao = reportingDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
    }

    /** BR-30, BR-32: open a new request. New requests are always OPEN and carry no cost. */
    public MaintenanceRequest open(MaintenanceRequest request) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        if (request == null) {
            throw new ValidationException("Request details are required.", null);
        }
        validateText(request);
        if (request.getPropertyId() == null) {
            throw new ValidationException("Select a property.", "property");
        }
        if (request.getReportedDate() == null) {
            throw new ValidationException("Reported date is required.", "reportedDate");
        }
        if (request.getReportedDate().isAfter(LocalDate.now(clock))) {
            throw new ValidationException("Reported date cannot be in the future.", "reportedDate");
        }
        Property property = propertyDao.findById(request.getPropertyId())
                .orElseThrow(() -> new EntityNotFoundException("The selected property no longer exists."));
        if (!property.isActive()) {
            throw new BusinessRuleException("Property " + property.getName()
                    + " is inactive; maintenance requests can only be opened for active properties.", "BR-30");
        }
        if (request.getTenantId() != null
                && !agreementDao.existsForPropertyAndTenant(request.getPropertyId(), request.getTenantId())) {
            throw new BusinessRuleException("The selected tenant has no agreement on " + property.getName() + ".", "BR-30");
        }
        if (request.getCost() != null) {
            throw new BusinessRuleException("Cost can only be set once work is in progress or resolved.", "BR-32");
        }
        request.setStatus(MaintenanceStatus.OPEN);
        request.setResolvedDate(null);
        MaintenanceRequest saved = tx.inTransaction(() -> {
            MaintenanceRequest inserted = maintenanceDao.insert(request);
            audit.record("MAINTENANCE", inserted.getRequestId(), "CREATED", null, AuditService.details(
                    "propertyId", inserted.getPropertyId(), "title", inserted.getTitle(), "priority", inserted.getPriority().name()));
            return inserted;
        });
        log.info("Maintenance request {} opened for property {}", saved.getRequestId(), saved.getPropertyId());
        return saved;
    }

    /** Edits title, description, priority, assignee, and cost (BR-32); status changes use changeStatus. */
    public MaintenanceRequest update(MaintenanceRequest edited) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        MaintenanceRequest existing = get(edited.getRequestId());
        validateText(edited);
        if (edited.getCost() != null) {
            requireCostAllowed(existing.getStatus());
        }
        existing.setTitle(edited.getTitle());
        existing.setDescription(edited.getDescription());
        existing.setPriority(edited.getPriority());
        existing.setAssignedTo(edited.getAssignedTo());
        existing.setCost(edited.getCost());
        tx.runInTransaction(() -> {
            saveExisting(existing);
            audit.record("MAINTENANCE", existing.getRequestId(), "UPDATED", null, AuditService.details(
                    "title", existing.getTitle(), "priority", existing.getPriority().name(), "cost", existing.getCost()));
        });
        return existing;
    }

    /** BR-31: workflow transition; RESOLVED requires a resolved date, other statuses clear it. */
    public MaintenanceRequest changeStatus(Long requestId, MaintenanceStatus target, LocalDate resolvedDate)
            throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        MaintenanceRequest request = get(requestId);
        MaintenanceStatus before = request.getStatus();
        if (target == null || !request.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException("A request cannot move from " + request.getStatus() + " to " + target + ".", "BR-31");
        }
        if (target == MaintenanceStatus.RESOLVED) {
            LocalDate today = LocalDate.now(clock);
            LocalDate date = resolvedDate == null ? today : resolvedDate;
            if (date.isBefore(request.getReportedDate())) {
                throw new BusinessRuleException("The resolved date cannot be before the reported date ("
                        + request.getReportedDate() + ").", "BR-31");
            }
            if (date.isAfter(today)) {
                throw new BusinessRuleException("The resolved date cannot be in the future.", "BR-31");
            }
            request.setResolvedDate(date);
        } else {
            request.setResolvedDate(null);
        }
        if (target == MaintenanceStatus.CANCELLED && request.getCost() != null) {
            throw new BusinessRuleException("Clear the cost before cancelling; cost is only allowed while work is in progress or resolved.", "BR-32");
        }
        request.setStatus(target);
        tx.runInTransaction(() -> {
            saveExisting(request);
            audit.record("MAINTENANCE", requestId, "STATUS_CHANGED", AuditService.details("status", before.name()),
                    AuditService.details("status", target.name(), "resolvedDate", request.getResolvedDate()));
        });
        log.info("Maintenance request {} moved to {}", requestId, target);
        return request;
    }

    public MaintenanceRequest get(Long requestId) throws EntityNotFoundException {
        auth.require(Permission.VIEW_DATA);
        if (requestId == null) {
            throw new EntityNotFoundException("Request not found.");
        }
        return maintenanceDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request " + requestId + " no longer exists."));
    }

    /** Joined rows with property and tenant names; a null status returns everything. */
    public List<MaintenanceReportRow> findRows(MaintenanceStatus statusOrNull) {
        auth.require(Permission.VIEW_DATA);
        return reportingDao.findMaintenanceRows(statusOrNull);
    }

    /** Counts, recorded costs, average resolution time and cost trends (last six months, zero-filled). */
    public MaintenanceSummary summary() {
        auth.require(Permission.VIEW_DATA);
        YearMonth thisMonth = YearMonth.from(LocalDate.now(clock));
        MaintenanceSummary raw = reportingDao.maintenanceSummary(thisMonth.minusMonths(5).atDay(1), thisMonth.atEndOfMonth());
        return new MaintenanceSummary(raw.countByStatus(), raw.openByPriority(), raw.resolvedCost(), raw.inProgressCost(),
                raw.averageResolutionDays(), raw.costByProperty(), MonthlyAmount.fill(raw.costByMonth(), thisMonth, 6));
    }

    private void validateText(MaintenanceRequest r) throws ValidationException {
        Validators.requireNonBlank(r.getTitle(), "title", "Title is required");
        if (r.getTitle().length() > 120) {
            throw new ValidationException("Title cannot exceed 120 characters.", "title");
        }
        if (r.getPriority() == null) {
            r.setPriority(MaintenancePriority.MEDIUM);
        }
        if (r.getAssignedTo() != null && r.getAssignedTo().length() > 100) {
            throw new ValidationException("Assignee cannot exceed 100 characters.", "assignedTo");
        }
        if (r.getCost() != null) {
            Validators.validateNonNegativeMoney(r.getCost(), "cost", "Cost cannot be negative");
        }
    }

    private void requireCostAllowed(MaintenanceStatus status) throws BusinessRuleException {
        if (status != MaintenanceStatus.IN_PROGRESS && status != MaintenanceStatus.RESOLVED) {
            throw new BusinessRuleException("Cost can only be set while the request is in progress or resolved.", "BR-32");
        }
    }

    private void saveExisting(MaintenanceRequest request) throws EntityNotFoundException {
        if (maintenanceDao.update(request) == 0) {
            throw new EntityNotFoundException("The request was changed by someone else. Refresh and try again.");
        }
    }
}
