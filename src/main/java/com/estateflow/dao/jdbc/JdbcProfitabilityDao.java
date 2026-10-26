package com.estateflow.dao.jdbc;

import com.estateflow.dao.ProfitabilityDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.enums.ExpenseCategory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class JdbcProfitabilityDao extends BaseJdbcDao implements ProfitabilityDao {

    public JdbcProfitabilityDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    private static int bindRange(PreparedStatement ps, int next, LocalDate from, LocalDate to, Long propertyId) throws SQLException {
        ps.setObject(next++, from);
        ps.setObject(next++, to);
        if (propertyId != null) {
            ps.setLong(next++, propertyId);
        }
        return next;
    }

    @Override
    public List<PropertyRef> properties(Long propertyIdOrNull) {
        return queryList("list properties for profitability", "SELECT property_id, name, is_active FROM property "
                + (propertyIdOrNull == null ? "" : "WHERE property_id = ? ") + "ORDER BY name", ps -> {
            if (propertyIdOrNull != null) {
                ps.setLong(1, propertyIdOrNull);
            }
        }, rs -> new PropertyRef(rs.getLong("property_id"), rs.getString("name"), rs.getBoolean("is_active")));
    }

    @Override
    public List<IncomeRow> receivedRent(LocalDate from, LocalDate to, Long propertyIdOrNull) {
        String filter = propertyIdOrNull == null ? "" : "AND ra.property_id = ? ";
        return queryList("sum received rent", "SELECT ra.property_id, DATE_FORMAT(p.payment_date, '%Y-%m') AS ym, SUM(p.amount) AS total "
                + "FROM payment p JOIN rent_charge c ON c.charge_id = p.charge_id "
                + "JOIN rental_agreement ra ON ra.agreement_id = c.agreement_id "
                + "WHERE p.voided_at IS NULL AND p.payment_date BETWEEN ? AND ? " + filter
                + "GROUP BY ra.property_id, ym", ps -> bindRange(ps, 1, from, to, propertyIdOrNull),
                rs -> new IncomeRow(rs.getLong("property_id"), YearMonth.parse(rs.getString("ym")), rs.getBigDecimal("total")));
    }

    @Override
    public List<ExpenseAggRow> expenses(LocalDate from, LocalDate to, Long propertyIdOrNull) {
        String ef = propertyIdOrNull == null ? "" : "AND e.property_id = ? ";
        String mf = propertyIdOrNull == null ? "" : "AND m.property_id = ? ";
        return queryList("sum expenses", "SELECT e.property_id, DATE_FORMAT(e.expense_date, '%Y-%m') AS ym, e.category AS category, "
                + "SUM(e.amount) AS total, 0 AS from_request FROM expense e "
                + "WHERE e.deleted_at IS NULL AND e.expense_date BETWEEN ? AND ? " + ef
                + "GROUP BY e.property_id, ym, e.category "
                + "UNION ALL "
                + "SELECT m.property_id, DATE_FORMAT(m.resolved_date, '%Y-%m') AS ym, 'MAINTENANCE' AS category, "
                + "SUM(m.cost) AS total, 1 AS from_request FROM maintenance_request m "
                + "WHERE m.status = 'RESOLVED' AND m.cost > 0 AND m.resolved_date BETWEEN ? AND ? " + mf
                + "AND NOT EXISTS (SELECT 1 FROM expense x WHERE x.maintenance_request_id = m.request_id AND x.deleted_at IS NULL) "
                + "GROUP BY m.property_id, ym", ps -> {
            int next = bindRange(ps, 1, from, to, propertyIdOrNull);
            bindRange(ps, next, from, to, propertyIdOrNull);
        }, rs -> new ExpenseAggRow(rs.getLong("property_id"), YearMonth.parse(rs.getString("ym")),
                ExpenseCategory.valueOf(rs.getString("category")), rs.getBigDecimal("total"), rs.getInt("from_request") == 1));
    }

    @Override
    public List<ChargedRow> chargedRent(LocalDate from, LocalDate to, Long propertyIdOrNull) {
        String filter = propertyIdOrNull == null ? "" : "AND ra.property_id = ? ";
        return queryList("sum charged rent", "SELECT ra.property_id, SUM(c.amount_due) AS total FROM rent_charge c "
                + "JOIN rental_agreement ra ON ra.agreement_id = c.agreement_id "
                + "WHERE c.cancelled_at IS NULL AND c.due_date BETWEEN ? AND ? " + filter + "GROUP BY ra.property_id",
                ps -> bindRange(ps, 1, from, to, propertyIdOrNull),
                rs -> new ChargedRow(rs.getLong("property_id"), rs.getBigDecimal("total")));
    }
}
