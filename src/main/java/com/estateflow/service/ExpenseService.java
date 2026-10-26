package com.estateflow.service;

import com.estateflow.dao.ExpenseDao;
import com.estateflow.dao.MaintenanceDao;
import com.estateflow.dao.PropertyDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.dto.ExpenseRow;
import com.estateflow.dto.LinkableMaintenance;
import com.estateflow.exception.*;
import com.estateflow.model.Expense;
import com.estateflow.model.MaintenanceRequest;
import com.estateflow.model.Property;
import com.estateflow.model.enums.ExpenseCategory;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Property expenses. Reads need VIEW_DATA; create, edit and delete need MANAGE_RECORDS (ADMIN and MANAGER; VIEWER is
 * read-only). Deleting is a soft delete: the row and its reason stay, and every action is audited with the acting user
 * in the same transaction as the change.
 */
public class ExpenseService {

    static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final int MAX_LIST = 1000;

    private final ExpenseDao expenseDao;
    private final PropertyDao propertyDao;
    private final MaintenanceDao maintenanceDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;
    private final Authorizer auth;

    public ExpenseService(ExpenseDao expenseDao, PropertyDao propertyDao, MaintenanceDao maintenanceDao,
                          TransactionManager tx, AuditService audit, Clock clock, Authorizer auth) {
        this.expenseDao = expenseDao;
        this.propertyDao = propertyDao;
        this.maintenanceDao = maintenanceDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.auth = auth;
    }

    /** Fails with a setup message when the v1.4 migration has not been applied. */
    public void ensureReady() {
        auth.require(Permission.VIEW_DATA);
        if (!expenseDao.isAvailable()) {
            throw new ConfigurationException("The expense table is missing. Apply db/migrations/v1.4_expense.sql "
                    + "to the estateflow database (see README), then reopen this screen.");
        }
    }

    public Expense create(Expense input) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        Expense e = validate(input, null);
        try {
            Expense saved = tx.inTransaction(() -> {
                Expense inserted = expenseDao.insert(e);
                audit.record("EXPENSE", inserted.getExpenseId(), "CREATED", null, snapshot(inserted));
                return inserted;
            });
            return saved;
        } catch (DuplicateKeyException ex) {
            throw new BusinessRuleException("That maintenance request already has an expense recorded against it.", "EXP-03");
        }
    }

    /** Read-only lookup for linking a completed work order to its canonical NOI expense. */
    public Expense getForWorkOrderLink(long expenseId) throws EntityNotFoundException {
        auth.require(Permission.VIEW_DATA);
        ensureReady();
        Expense expense=expenseDao.findById(expenseId).orElseThrow(()->new EntityNotFoundException("Expense no longer exists."));
        if(expense.isDeleted()) throw new EntityNotFoundException("That expense is no longer active.");
        return expense;
    }

    public Expense update(Expense input) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        if (input == null || input.getExpenseId() == null) {
            throw new ValidationException("Expense details are required.", null);
        }
        Expense e = validate(input, input.getExpenseId());
        try {
            return tx.inTransaction(() -> {
                Expense before = expenseDao.findById(e.getExpenseId())
                        .orElseThrow(() -> new EntityNotFoundException("That expense no longer exists."));
                if (before.isDeleted()) {
                    throw new BusinessRuleException("A deleted expense cannot be edited.", "EXP-04");
                }
                if (expenseDao.update(e) == 0) {
                    throw new BusinessRuleException("A deleted expense cannot be edited.", "EXP-04");
                }
                Expense after = expenseDao.findById(e.getExpenseId()).orElseThrow();
                audit.record("EXPENSE", after.getExpenseId(), "UPDATED", snapshot(before), snapshot(after));
                return after;
            });
        } catch (DuplicateKeyException ex) {
            throw new BusinessRuleException("That maintenance request already has an expense recorded against it.", "EXP-03");
        }
    }

    /** Soft delete: the expense stays in the database, flagged with the reason, and stops counting everywhere. */
    public void delete(long expenseId, String reason) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > 255) {
            throw new ValidationException("Enter a reason for deleting (1 to 255 characters).", "reason");
        }
        tx.runInTransaction(() -> {
            Expense before = expenseDao.findById(expenseId)
                    .orElseThrow(() -> new EntityNotFoundException("That expense no longer exists."));
            if (before.isDeleted()) {
                throw new BusinessRuleException("This expense is already deleted.", "EXP-04");
            }
            if (expenseDao.softDelete(expenseId, why, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)) == 0) {
                throw new BusinessRuleException("This expense is already deleted.", "EXP-04");
            }
            Map<String, Object> old = snapshot(before);
            Map<String, Object> deleted = new LinkedHashMap<>(old);
            deleted.put("deleted", true);
            deleted.put("reason", why);
            audit.record("EXPENSE", expenseId, "DELETED", old, deleted);
        });
    }

    public Expense get(long expenseId) throws EntityNotFoundException {
        auth.require(Permission.VIEW_DATA);
        return expenseDao.findById(expenseId).orElseThrow(() -> new EntityNotFoundException("That expense no longer exists."));
    }

    /** Newest first. Deleted expenses are listed only when asked for (they never count in any total). */
    public List<ExpenseRow> search(Long propertyId, ExpenseCategory category, LocalDate from, LocalDate to,
                                   boolean includeDeleted) throws ValidationException {
        auth.require(Permission.VIEW_DATA);
        if (from != null && to != null && to.isBefore(from)) {
            throw new ValidationException("The 'to' date cannot be before the 'from' date.", "dateRange");
        }
        return expenseDao.search(propertyId, category, from, to, includeDeleted, MAX_LIST);
    }

    public List<LinkableMaintenance> linkableMaintenance(long propertyId, Long currentRequestId) {
        auth.require(Permission.VIEW_DATA);
        return expenseDao.findLinkableMaintenance(propertyId, currentRequestId);
    }

    // ---- validation ----

    private Expense validate(Expense in, Long excludeId) throws EstateFlowException {
        if (in == null) {
            throw new ValidationException("Expense details are required.", null);
        }
        if (in.getPropertyId() == null) {
            throw new ValidationException("Select a property.", "property");
        }
        if (in.getCategory() == null) {
            throw new ValidationException("Select a category.", "category");
        }
        BigDecimal amount = in.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("Amount must be greater than zero.", "amount");
        }
        if (!MoneyUtil.hasValidScale(amount)) {
            throw new ValidationException("Amount can have at most 2 decimal places.", "amount");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException("Amount is too large.", "amount");
        }
        LocalDate date = in.getExpenseDate();
        if (date == null) {
            throw new ValidationException("Expense date is required.", "expenseDate");
        }
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ValidationException("Expense date cannot be in the future.", "expenseDate");
        }
        if (date.isBefore(EARLIEST_DATE)) {
            throw new ValidationException("Expense date cannot be before " + EARLIEST_DATE + ".", "expenseDate");
        }
        String description = in.getDescription() == null ? "" : in.getDescription().trim();
        if (description.isEmpty() || description.length() > 255) {
            throw new ValidationException("Description is required (1 to 255 characters).", "description");
        }
        if (in.getPaymentMethod() == null) {
            throw new ValidationException("Select a payment method.", "paymentMethod");
        }
        String reference = in.getReferenceNo() == null || in.getReferenceNo().isBlank() ? null : in.getReferenceNo().trim();
        if (reference != null && reference.length() > 60) {
            throw new ValidationException("Reference number can be at most 60 characters.", "referenceNo");
        }
        Property property = propertyDao.findById(in.getPropertyId())
                .orElseThrow(() -> new EntityNotFoundException("The selected property no longer exists."));
        if (in.getMaintenanceRequestId() != null) {
            MaintenanceRequest request = maintenanceDao.findById(in.getMaintenanceRequestId())
                    .orElseThrow(() -> new EntityNotFoundException("The linked maintenance request no longer exists."));
            if (!request.getPropertyId().equals(property.getPropertyId())) {
                throw new BusinessRuleException("The maintenance request belongs to a different property.", "EXP-02");
            }
            if (request.getStatus() == MaintenanceStatus.CANCELLED) {
                throw new BusinessRuleException("A cancelled maintenance request cannot have an expense.", "EXP-02");
            }
            if (in.getCategory() != ExpenseCategory.MAINTENANCE && in.getCategory() != ExpenseCategory.REPAIRS) {
                throw new BusinessRuleException("An expense linked to a maintenance request must be in the Maintenance "
                        + "or Repairs category.", "EXP-02");
            }
        }
        if (expenseDao.existsIdentical(property.getPropertyId(), in.getCategory(), amount, date, description, excludeId)) {
            throw new BusinessRuleException("An identical expense (same property, category, amount, date and description) "
                    + "is already recorded. Change the description if this is a separate payment.", "EXP-01");
        }
        Expense out = new Expense();
        out.setExpenseId(in.getExpenseId());
        out.setPropertyId(property.getPropertyId());
        out.setMaintenanceRequestId(in.getMaintenanceRequestId());
        out.setCategory(in.getCategory());
        out.setAmount(amount.setScale(2));
        out.setExpenseDate(date);
        out.setDescription(description);
        out.setPaymentMethod(in.getPaymentMethod());
        out.setReferenceNo(reference);
        return out;
    }

    private static Map<String, Object> snapshot(Expense e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propertyId", e.getPropertyId());
        m.put("category", e.getCategory().name());
        m.put("amount", e.getAmount().toPlainString());
        m.put("date", String.valueOf(e.getExpenseDate()));
        m.put("description", e.getDescription());
        m.put("method", e.getPaymentMethod().name());
        if (e.getReferenceNo() != null) {
            m.put("reference", e.getReferenceNo());
        }
        if (e.getMaintenanceRequestId() != null) {
            m.put("maintenanceRequestId", e.getMaintenanceRequestId());
        }
        return m;
    }
}
