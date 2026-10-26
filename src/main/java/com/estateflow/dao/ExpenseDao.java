package com.estateflow.dao;

import com.estateflow.dto.ExpenseRow;
import com.estateflow.dto.LinkableMaintenance;
import com.estateflow.model.Expense;
import com.estateflow.model.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence for property expenses. Rows are soft-deleted, never removed. */
public interface ExpenseDao {

    /** True when the expense table exists (v1.4 migration applied). */
    boolean isAvailable();

    /** Throws DuplicateKeyException when an active expense is already linked to the same maintenance request. */
    Expense insert(Expense expense);

    /** Updates an active (not deleted) expense; returns matched rows (0 when missing or already deleted). */
    int update(Expense expense);

    Optional<Expense> findById(long expenseId);

    /** Newest first; all filters optional; deleted rows only when {@code includeDeleted}. */
    List<ExpenseRow> search(Long propertyId, ExpenseCategory category, LocalDate from, LocalDate to,
                            boolean includeDeleted, int limit);

    /** Soft delete; returns matched rows (0 when missing or already deleted). */
    int softDelete(long expenseId, String reason, LocalDateTime deletedAtUtc);

    /** An identical ACTIVE expense (same property, category, amount, date, description) other than {@code excludeId}. */
    boolean existsIdentical(long propertyId, ExpenseCategory category, BigDecimal amount, LocalDate date,
                            String description, Long excludeId);

    /** Non-cancelled requests of the property with no active expense linked, plus {@code keepRequestId} if given. */
    List<LinkableMaintenance> findLinkableMaintenance(long propertyId, Long keepRequestId);
}
