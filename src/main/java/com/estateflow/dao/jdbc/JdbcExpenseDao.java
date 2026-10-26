package com.estateflow.dao.jdbc;

import com.estateflow.dao.ExpenseDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.dto.ExpenseRow;
import com.estateflow.dto.LinkableMaintenance;
import com.estateflow.model.Expense;
import com.estateflow.model.enums.ExpenseCategory;
import com.estateflow.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcExpenseDao extends BaseJdbcDao implements ExpenseDao {

    private static final String COLUMNS = "e.expense_id, e.property_id, e.maintenance_request_id, e.category, e.amount, "
            + "e.expense_date, e.description, e.payment_method, e.reference_no, e.created_at, e.updated_at, "
            + "e.deleted_at, e.delete_reason";

    private static final RowMapper<Expense> MAPPER = rs -> {
        Expense e = new Expense();
        e.setExpenseId(rs.getLong("expense_id"));
        e.setPropertyId(rs.getLong("property_id"));
        e.setMaintenanceRequestId(longOrNull(rs, "maintenance_request_id"));
        e.setCategory(ExpenseCategory.valueOf(rs.getString("category")));
        e.setAmount(rs.getBigDecimal("amount"));
        e.setExpenseDate(date(rs, "expense_date"));
        e.setDescription(rs.getString("description"));
        e.setPaymentMethod(PaymentMethod.valueOf(rs.getString("payment_method")));
        e.setReferenceNo(rs.getString("reference_no"));
        e.setCreatedAt(dateTime(rs, "created_at"));
        e.setUpdatedAt(dateTime(rs, "updated_at"));
        e.setDeletedAt(dateTime(rs, "deleted_at"));
        e.setDeleteReason(rs.getString("delete_reason"));
        return e;
    };

    public JdbcExpenseDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    private static void bindLink(PreparedStatement ps, int index, Long requestId) throws SQLException {
        if (requestId == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, requestId);
        }
    }

    @Override
    public boolean isAvailable() {
        return exists("check expense table", "SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'expense' LIMIT 1", Binder.NONE);
    }

    @Override
    public Expense insert(Expense e) {
        long id = insertReturningKey("insert expense", "INSERT INTO expense (property_id, maintenance_request_id, category, "
                + "amount, expense_date, description, payment_method, reference_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setLong(1, e.getPropertyId());
            bindLink(ps, 2, e.getMaintenanceRequestId());
            ps.setString(3, e.getCategory().name());
            ps.setBigDecimal(4, e.getAmount());
            ps.setObject(5, e.getExpenseDate());
            ps.setString(6, e.getDescription());
            ps.setString(7, e.getPaymentMethod().name());
            ps.setString(8, e.getReferenceNo());
        });
        return findById(id).orElseThrow(() -> new IllegalStateException("Inserted expense not found"));
    }

    @Override
    public int update(Expense e) {
        return executeUpdate("update expense", "UPDATE expense SET property_id = ?, maintenance_request_id = ?, category = ?, "
                + "amount = ?, expense_date = ?, description = ?, payment_method = ?, reference_no = ? "
                + "WHERE expense_id = ? AND deleted_at IS NULL", ps -> {
            ps.setLong(1, e.getPropertyId());
            bindLink(ps, 2, e.getMaintenanceRequestId());
            ps.setString(3, e.getCategory().name());
            ps.setBigDecimal(4, e.getAmount());
            ps.setObject(5, e.getExpenseDate());
            ps.setString(6, e.getDescription());
            ps.setString(7, e.getPaymentMethod().name());
            ps.setString(8, e.getReferenceNo());
            ps.setLong(9, e.getExpenseId());
        });
    }

    @Override
    public Optional<Expense> findById(long expenseId) {
        return queryOne("find expense", "SELECT " + COLUMNS + " FROM expense e WHERE e.expense_id = ?",
                ps -> ps.setLong(1, expenseId), MAPPER);
    }

    @Override
    public List<ExpenseRow> search(Long propertyId, ExpenseCategory category, LocalDate from, LocalDate to,
                                   boolean includeDeleted, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + ", pr.name AS property_name, m.title AS maintenance_title "
                + "FROM expense e JOIN property pr ON pr.property_id = e.property_id "
                + "LEFT JOIN maintenance_request m ON m.request_id = e.maintenance_request_id WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (propertyId != null) {
            sql.append("AND e.property_id = ? ");
            params.add(propertyId);
        }
        if (category != null) {
            sql.append("AND e.category = ? ");
            params.add(category.name());
        }
        if (from != null) {
            sql.append("AND e.expense_date >= ? ");
            params.add(from);
        }
        if (to != null) {
            sql.append("AND e.expense_date <= ? ");
            params.add(to);
        }
        if (!includeDeleted) {
            sql.append("AND e.deleted_at IS NULL ");
        }
        sql.append("ORDER BY e.expense_date DESC, e.expense_id DESC LIMIT ?");
        params.add(limit);
        return queryList("list expenses", sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, rs -> new ExpenseRow(MAPPER.map(rs), rs.getString("property_name"), rs.getString("maintenance_title")));
    }

    @Override
    public int softDelete(long expenseId, String reason, LocalDateTime deletedAtUtc) {
        return executeUpdate("delete expense", "UPDATE expense SET deleted_at = ?, delete_reason = ? "
                + "WHERE expense_id = ? AND deleted_at IS NULL", ps -> {
            ps.setObject(1, deletedAtUtc);
            ps.setString(2, reason);
            ps.setLong(3, expenseId);
        });
    }

    @Override
    public boolean existsIdentical(long propertyId, ExpenseCategory category, BigDecimal amount, LocalDate date,
                                   String description, Long excludeId) {
        return exists("check identical expense", "SELECT 1 FROM expense WHERE deleted_at IS NULL AND property_id = ? "
                + "AND category = ? AND amount = ? AND expense_date = ? AND LOWER(description) = LOWER(?) "
                + "AND expense_id <> ? LIMIT 1", ps -> {
            ps.setLong(1, propertyId);
            ps.setString(2, category.name());
            ps.setBigDecimal(3, amount);
            ps.setObject(4, date);
            ps.setString(5, description);
            ps.setLong(6, excludeId == null ? -1L : excludeId);
        });
    }

    @Override
    public List<LinkableMaintenance> findLinkableMaintenance(long propertyId, Long keepRequestId) {
        return queryList("list linkable maintenance requests", "SELECT m.request_id, m.title, m.status, m.cost, m.resolved_date "
                + "FROM maintenance_request m WHERE m.property_id = ? AND m.status <> 'CANCELLED' AND (m.request_id = ? "
                + "OR NOT EXISTS (SELECT 1 FROM expense e WHERE e.maintenance_request_id = m.request_id AND e.deleted_at IS NULL)) "
                + "ORDER BY m.request_id DESC", ps -> {
            ps.setLong(1, propertyId);
            ps.setLong(2, keepRequestId == null ? -1L : keepRequestId);
        }, rs -> new LinkableMaintenance(rs.getLong("request_id"), rs.getString("title"), rs.getString("status"),
                rs.getBigDecimal("cost"), date(rs, "resolved_date")));
    }
}
