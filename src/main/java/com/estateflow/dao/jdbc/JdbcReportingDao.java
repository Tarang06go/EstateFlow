package com.estateflow.dao.jdbc;

import com.estateflow.dao.ReportingDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.dto.*;
import com.estateflow.model.Payment;
import com.estateflow.model.enums.*;
import com.estateflow.util.MoneyUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only joined and aggregate queries; every date comes in as a bound parameter. */
public class JdbcReportingDao extends BaseJdbcDao implements ReportingDao {

    /** Charges that are live and already due, with their balance. */
    private static final String DUE_CHARGES = "SELECT rc.charge_id, rc.due_date, rc.amount_due - " + SqlFragments.AMOUNT_PAID
            + " AS balance FROM rent_charge rc WHERE " + SqlFragments.LIVE_CHARGE + " AND rc.due_date <= ?";

    public JdbcReportingDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public DashboardStats dashboardStats(LocalDate today, int warningDays, LocalDate monthStart, LocalDate monthEnd) {
        return withConnection("load dashboard statistics", c -> {
            int total = one(c, "SELECT COUNT(*) AS n FROM property WHERE is_active = 1", Binder.NONE, rs -> rs.getInt("n")).orElse(0);
            int occupied = one(c, "SELECT COUNT(DISTINCT pr.property_id) AS n FROM property pr JOIN rental_agreement ra "
                            + "ON ra.property_id = pr.property_id WHERE pr.is_active = 1 AND " + SqlFragments.OCCUPIES,
                    ps -> {
                        ps.setObject(1, today);
                        ps.setObject(2, today);
                    }, rs -> rs.getInt("n")).orElse(0);
            int agreements = one(c, "SELECT COUNT(*) AS n FROM rental_agreement ra WHERE " + SqlFragments.OCCUPIES,
                    ps -> {
                        ps.setObject(1, today);
                        ps.setObject(2, today);
                    }, rs -> rs.getInt("n")).orElse(0);
            int ending = one(c, "SELECT COUNT(*) AS n FROM rental_agreement ra WHERE " + SqlFragments.EFFECTIVE_END
                            + " BETWEEN ? AND ?", ps -> {
                        ps.setObject(1, today);
                        ps.setObject(2, today.plusDays(warningDays));
                    }, rs -> rs.getInt("n")).orElse(0);
            BigDecimal collected = one(c, "SELECT COALESCE(SUM(p.amount), 0) AS s FROM payment p WHERE "
                            + SqlFragments.VALID_PAYMENT + " AND p.payment_date BETWEEN ? AND ?", ps -> {
                        ps.setObject(1, monthStart);
                        ps.setObject(2, monthEnd);
                    }, rs -> rs.getBigDecimal("s")).orElse(BigDecimal.ZERO);
            Object[] balances = one(c, "SELECT COALESCE(SUM(x.balance), 0) AS outstanding, COALESCE(SUM(CASE WHEN "
                            + "x.due_date < ? AND x.balance > 0 THEN 1 ELSE 0 END), 0) AS overdue FROM (" + DUE_CHARGES + ") x",
                    ps -> {
                        ps.setObject(1, today);
                        ps.setObject(2, today);
                    }, rs -> new Object[]{rs.getBigDecimal("outstanding"), rs.getInt("overdue")})
                    .orElse(new Object[]{BigDecimal.ZERO, 0});
            Map<MaintenancePriority, Integer> byPriority = new EnumMap<>(MaintenancePriority.class);
            for (MaintenancePriority p : MaintenancePriority.values()) {
                byPriority.put(p, 0);
            }
            list(c, "SELECT priority, COUNT(*) AS n FROM maintenance_request WHERE status IN (?, ?) GROUP BY priority",
                    ps -> {
                        ps.setString(1, MaintenanceStatus.OPEN.name());
                        ps.setString(2, MaintenanceStatus.IN_PROGRESS.name());
                    }, rs -> byPriority.put(MaintenancePriority.valueOf(rs.getString("priority")), rs.getInt("n")));
            BigDecimal percent = total == 0 ? MoneyUtil.ZERO
                    : BigDecimal.valueOf(occupied).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            return new DashboardStats(total, occupied, total - occupied, percent, agreements, ending,
                    MoneyUtil.round(collected), MoneyUtil.round((BigDecimal) balances[0]), (Integer) balances[1], byPriority);
        });
    }

    @Override
    public List<OverdueRentRow> findOverdueRows(LocalDate today) {
        return queryList("list overdue rent", "SELECT rc.charge_id, rc.agreement_id, rc.period_start, rc.due_date, "
                + "rc.amount_due, " + SqlFragments.AMOUNT_PAID + " AS amount_paid, pr.name AS property_name, "
                + "t.full_name AS tenant_name, t.phone AS tenant_phone FROM rent_charge rc "
                + "JOIN rental_agreement ra ON ra.agreement_id = rc.agreement_id "
                + "JOIN property pr ON pr.property_id = ra.property_id JOIN tenant t ON t.tenant_id = ra.tenant_id "
                + "WHERE " + SqlFragments.LIVE_CHARGE + " AND rc.due_date < ? "
                + "AND rc.amount_due > " + SqlFragments.AMOUNT_PAID + " ORDER BY rc.due_date, rc.charge_id",
                ps -> ps.setObject(1, today), rs -> {
                    BigDecimal due = rs.getBigDecimal("amount_due");
                    BigDecimal paid = rs.getBigDecimal("amount_paid");
                    LocalDate dueDate = date(rs, "due_date");
                    return new OverdueRentRow(rs.getLong("charge_id"), rs.getLong("agreement_id"),
                            rs.getString("property_name"), rs.getString("tenant_name"), rs.getString("tenant_phone"),
                            date(rs, "period_start"), dueDate, due, paid, due.subtract(paid),
                            ChronoUnit.DAYS.between(dueDate, today));
                });
    }

    @Override
    public List<PropertyOccupancyRow> findOccupancyRows(LocalDate today) {
        return queryList("list property occupancy", "SELECT pr.property_id, pr.name, pr.city, pr.is_active, "
                + "pr.listed_rent, o.full_name AS owner_name, t.full_name AS tenant_name, ra.agreement_id "
                + "FROM property pr JOIN owner o ON o.owner_id = pr.owner_id "
                + "LEFT JOIN rental_agreement ra ON ra.property_id = pr.property_id AND " + SqlFragments.OCCUPIES + " "
                + "LEFT JOIN tenant t ON t.tenant_id = ra.tenant_id ORDER BY pr.name, pr.property_id", ps -> {
            ps.setObject(1, today);
            ps.setObject(2, today);
        }, rs -> new PropertyOccupancyRow(rs.getLong("property_id"), rs.getString("name"), rs.getString("city"),
                rs.getString("owner_name"), rs.getBoolean("is_active"), longOrNull(rs, "agreement_id") != null,
                rs.getString("tenant_name"), rs.getBigDecimal("listed_rent")));
    }

    private static final String PAYMENT_ROW_SELECT = "SELECT p.payment_id, p.payment_date, p.amount, p.payment_method, "
            + "p.reference_no, p.voided_at, p.void_reason, rc.period_start, pr.name AS property_name, "
            + "t.full_name AS tenant_name FROM payment p JOIN rent_charge rc ON rc.charge_id = p.charge_id "
            + "JOIN rental_agreement ra ON ra.agreement_id = rc.agreement_id "
            + "JOIN property pr ON pr.property_id = ra.property_id JOIN tenant t ON t.tenant_id = ra.tenant_id ";

    private static final RowMapper<PaymentReportRow> PAYMENT_ROW_MAPPER = rs -> {
        LocalDate paymentDate = date(rs, "payment_date");
        long id = rs.getLong("payment_id");
        return new PaymentReportRow(id, Payment.formatReceiptNumber(paymentDate, id), paymentDate,
                rs.getString("tenant_name"), rs.getString("property_name"), date(rs, "period_start"),
                rs.getBigDecimal("amount"), PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getString("reference_no"), rs.getObject("voided_at") != null, rs.getString("void_reason"));
    };

    private static final String MAINTENANCE_ROW_SELECT = "SELECT m.request_id, m.property_id, pr.name AS property_name, "
            + "t.full_name AS tenant_name, m.title, m.priority, m.status, m.reported_date, m.resolved_date, "
            + "m.assigned_to, m.cost FROM maintenance_request m JOIN property pr ON pr.property_id = m.property_id "
            + "LEFT JOIN tenant t ON t.tenant_id = m.tenant_id ";

    private static final RowMapper<MaintenanceReportRow> MAINTENANCE_ROW_MAPPER = rs -> new MaintenanceReportRow(
            rs.getLong("request_id"), rs.getLong("property_id"), rs.getString("property_name"), rs.getString("tenant_name"),
            rs.getString("title"), MaintenancePriority.valueOf(rs.getString("priority")),
            MaintenanceStatus.valueOf(rs.getString("status")), date(rs, "reported_date"), date(rs, "resolved_date"),
            rs.getString("assigned_to"), rs.getBigDecimal("cost"));

    @Override
    public List<PaymentReportRow> findPaymentRows(LocalDate from, LocalDate to) {
        return queryList("list payment report rows", PAYMENT_ROW_SELECT + "WHERE p.payment_date BETWEEN ? AND ? "
                + "ORDER BY p.payment_date, p.payment_id", ps -> {
            ps.setObject(1, from);
            ps.setObject(2, to);
        }, PAYMENT_ROW_MAPPER);
    }

    @Override
    public List<PaymentReportRow> findPaymentRowsForProperty(Long propertyId) {
        return queryList("list payments of property", PAYMENT_ROW_SELECT + "WHERE pr.property_id = ? "
                + "ORDER BY p.payment_date DESC, p.payment_id DESC", ps -> ps.setLong(1, propertyId), PAYMENT_ROW_MAPPER);
    }

    @Override
    public List<PaymentReportRow> searchPaymentRows(String text, LocalDate fromOrNull, LocalDate toOrNull,
                                                    Boolean voidedOrNull, int limit) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (fromOrNull != null) {
            where.append("AND p.payment_date >= ? ");
            params.add(fromOrNull);
        }
        if (toOrNull != null) {
            where.append("AND p.payment_date <= ? ");
            params.add(toOrNull);
        }
        if (voidedOrNull != null) {
            where.append(voidedOrNull ? "AND p.voided_at IS NOT NULL " : "AND " + SqlFragments.VALID_PAYMENT + " ");
        }
        String needle = text == null ? "" : text.trim();
        if (!needle.isEmpty()) {
            where.append("AND (t.full_name LIKE ? OR pr.name LIKE ? OR p.reference_no LIKE ? OR p.payment_id = ?) ");
            params.add(like(needle));
            params.add(like(needle));
            params.add(like(needle));
            params.add(receiptId(needle));
        }
        params.add(limit);
        return queryList("search payments", PAYMENT_ROW_SELECT + where
                + "ORDER BY p.payment_date DESC, p.payment_id DESC LIMIT ?", ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, PAYMENT_ROW_MAPPER);
    }

    /** "RCP-2026-000012", "#12" or "12" all identify payment 12; anything else matches no id. */
    static long receiptId(String text) {
        String digits = text.matches("(?i)RCP-\\d{4}-\\d+") ? text.substring(text.lastIndexOf('-') + 1)
                : text.startsWith("#") ? text.substring(1) : text;
        return digits.matches("\\d{1,18}") ? Long.parseLong(digits) : -1L;
    }

    @Override
    public List<MaintenanceReportRow> findMaintenanceRows(MaintenanceStatus statusOrNull) {
        String filter = statusOrNull == null ? "" : "WHERE m.status = ? ";
        return queryList("list maintenance report rows", MAINTENANCE_ROW_SELECT + filter
                + "ORDER BY m.reported_date DESC, m.request_id DESC", ps -> {
            if (statusOrNull != null) {
                ps.setString(1, statusOrNull.name());
            }
        }, MAINTENANCE_ROW_MAPPER);
    }

    @Override
    public List<MaintenanceReportRow> findMaintenanceRowsForProperty(Long propertyId) {
        return queryList("list maintenance of property", MAINTENANCE_ROW_SELECT + "WHERE m.property_id = ? "
                + "ORDER BY m.reported_date DESC, m.request_id DESC", ps -> ps.setLong(1, propertyId), MAINTENANCE_ROW_MAPPER);
    }

    @Override
    public List<MonthlyAmount> monthlyCollections(LocalDate from, LocalDate to) {
        return queryList("sum collections per month", "SELECT DATE_FORMAT(p.payment_date, '%Y-%m') AS ym, "
                + "SUM(p.amount) AS total FROM payment p WHERE " + SqlFragments.VALID_PAYMENT
                + " AND p.payment_date BETWEEN ? AND ? GROUP BY ym ORDER BY ym", ps -> {
            ps.setObject(1, from);
            ps.setObject(2, to);
        }, rs -> new MonthlyAmount(YearMonth.parse(rs.getString("ym")), rs.getBigDecimal("total")));
    }

    @Override
    public List<MonthlyAmount> monthlyMaintenanceCosts(LocalDate from, LocalDate to) {
        return queryList("sum maintenance cost per month", "SELECT DATE_FORMAT(COALESCE(m.resolved_date, m.reported_date), '%Y-%m') AS ym, "
                + "SUM(m.cost) AS total FROM maintenance_request m WHERE m.cost IS NOT NULL AND m.status IN ('IN_PROGRESS', 'RESOLVED') "
                + "AND COALESCE(m.resolved_date, m.reported_date) BETWEEN ? AND ? GROUP BY ym ORDER BY ym", ps -> {
            ps.setObject(1, from);
            ps.setObject(2, to);
        }, rs -> new MonthlyAmount(YearMonth.parse(rs.getString("ym")), rs.getBigDecimal("total")));
    }

    @Override
    public List<PropertyOccupancyRow> searchPropertyRows(String text, LocalDate today, int limit) {
        String needle = text == null ? "" : text.trim();
        return queryList("search property rows", "SELECT pr.property_id, pr.name, pr.city, pr.is_active, pr.listed_rent, "
                + "o.full_name AS owner_name, t.full_name AS tenant_name, ra.agreement_id FROM property pr "
                + "JOIN owner o ON o.owner_id = pr.owner_id LEFT JOIN rental_agreement ra ON ra.property_id = pr.property_id AND "
                + SqlFragments.OCCUPIES + " LEFT JOIN tenant t ON t.tenant_id = ra.tenant_id "
                + "WHERE (? = '' OR pr.name LIKE ? OR pr.city LIKE ? OR pr.address_line LIKE ? OR o.full_name LIKE ?) "
                + "ORDER BY pr.name, pr.property_id LIMIT ?", ps -> {
            ps.setObject(1, today);
            ps.setObject(2, today);
            ps.setString(3, needle);
            for (int i = 4; i <= 7; i++) {
                ps.setString(i, like(needle));
            }
            ps.setInt(8, limit);
        }, rs -> new PropertyOccupancyRow(rs.getLong("property_id"), rs.getString("name"), rs.getString("city"),
                rs.getString("owner_name"), rs.getBoolean("is_active"), longOrNull(rs, "agreement_id") != null,
                rs.getString("tenant_name"), rs.getBigDecimal("listed_rent")));
    }

    @Override
    public BigDecimal outstandingForProperty(Long propertyId, LocalDate today) {
        return queryOne("sum outstanding of property", "SELECT COALESCE(SUM(rc.amount_due - " + SqlFragments.AMOUNT_PAID
                + "), 0) AS outstanding FROM rent_charge rc JOIN rental_agreement ra ON ra.agreement_id = rc.agreement_id "
                + "WHERE ra.property_id = ? AND " + SqlFragments.LIVE_CHARGE + " AND rc.due_date <= ?", ps -> {
            ps.setLong(1, propertyId);
            ps.setObject(2, today);
        }, rs -> rs.getBigDecimal("outstanding")).orElse(BigDecimal.ZERO);
    }

    @Override
    public List<RentReminderRow> findRentReminders(LocalDate today, int windowDays) {
        return queryList("list rent reminders", "SELECT rc.charge_id, rc.agreement_id, rc.period_start, rc.due_date, "
                + "rc.amount_due - " + SqlFragments.AMOUNT_PAID + " AS balance, pr.name AS property_name, "
                + "t.full_name AS tenant_name, t.phone AS tenant_phone FROM rent_charge rc "
                + "JOIN rental_agreement ra ON ra.agreement_id = rc.agreement_id "
                + "JOIN property pr ON pr.property_id = ra.property_id JOIN tenant t ON t.tenant_id = ra.tenant_id "
                + "WHERE " + SqlFragments.LIVE_CHARGE + " AND rc.due_date <= ? AND rc.amount_due > " + SqlFragments.AMOUNT_PAID
                + " ORDER BY rc.due_date, rc.charge_id", ps -> ps.setObject(1, today.plusDays(windowDays)), rs -> {
            LocalDate due = date(rs, "due_date");
            return new RentReminderRow(rs.getLong("charge_id"), rs.getLong("agreement_id"), rs.getString("property_name"),
                    rs.getString("tenant_name"), rs.getString("tenant_phone"), date(rs, "period_start"), due,
                    rs.getBigDecimal("balance"), ChronoUnit.DAYS.between(today, due));
        });
    }

    @Override
    public MaintenanceSummary maintenanceSummary(LocalDate monthsFrom, LocalDate monthsTo) {
        Map<MaintenanceStatus, Integer> byStatus = new EnumMap<>(MaintenanceStatus.class);
        for (MaintenanceStatus s : MaintenanceStatus.values()) {
            byStatus.put(s, 0);
        }
        queryList("count maintenance by status", "SELECT status, COUNT(*) AS n FROM maintenance_request GROUP BY status",
                Binder.NONE, rs -> byStatus.put(MaintenanceStatus.valueOf(rs.getString("status")), rs.getInt("n")));
        Map<MaintenancePriority, Integer> open = new EnumMap<>(MaintenancePriority.class);
        for (MaintenancePriority p : MaintenancePriority.values()) {
            open.put(p, 0);
        }
        queryList("count open maintenance by priority", "SELECT priority, COUNT(*) AS n FROM maintenance_request "
                + "WHERE status IN ('OPEN', 'IN_PROGRESS') GROUP BY priority", Binder.NONE,
                rs -> open.put(MaintenancePriority.valueOf(rs.getString("priority")), rs.getInt("n")));
        BigDecimal[] costs = queryOne("sum maintenance costs", "SELECT COALESCE(SUM(CASE WHEN status = 'RESOLVED' THEN cost END), 0) AS resolved, "
                + "COALESCE(SUM(CASE WHEN status = 'IN_PROGRESS' THEN cost END), 0) AS in_progress FROM maintenance_request "
                + "WHERE cost IS NOT NULL", Binder.NONE,
                rs -> new BigDecimal[]{rs.getBigDecimal("resolved"), rs.getBigDecimal("in_progress")})
                .orElse(new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        BigDecimal avgDays = queryOne("average resolution time", "SELECT COUNT(*) AS n, COALESCE(SUM(DATEDIFF(resolved_date, reported_date)), 0) AS days "
                + "FROM maintenance_request WHERE status = 'RESOLVED'", Binder.NONE, rs -> rs.getInt("n") == 0 ? null
                : BigDecimal.valueOf(rs.getLong("days")).divide(BigDecimal.valueOf(rs.getInt("n")), 1, RoundingMode.HALF_UP))
                .orElse(null);
        List<PropertyCost> perProperty = queryList("maintenance cost per property", "SELECT pr.property_id, pr.name, COUNT(*) AS n, "
                + "SUM(m.cost) AS total FROM maintenance_request m JOIN property pr ON pr.property_id = m.property_id "
                + "WHERE m.cost IS NOT NULL AND m.status IN ('IN_PROGRESS', 'RESOLVED') GROUP BY pr.property_id, pr.name "
                + "ORDER BY total DESC, pr.name LIMIT 5", Binder.NONE, rs -> new PropertyCost(rs.getLong("property_id"),
                rs.getString("name"), rs.getInt("n"), rs.getBigDecimal("total")));
        return new MaintenanceSummary(byStatus, open, costs[0], costs[1], avgDays, perProperty,
                monthlyMaintenanceCosts(monthsFrom, monthsTo));
    }

    @Override
    public Optional<ReceiptContext> findReceiptContext(Long paymentId) {
        return queryOne("load receipt context", "SELECT t.full_name AS tenant_name, pr.name AS property_name, "
                + "rc.period_start, rc.period_end, rc.amount_due - " + SqlFragments.AMOUNT_PAID + " AS balance "
                + "FROM payment pay JOIN rent_charge rc ON rc.charge_id = pay.charge_id "
                + "JOIN rental_agreement ra ON ra.agreement_id = rc.agreement_id "
                + "JOIN property pr ON pr.property_id = ra.property_id JOIN tenant t ON t.tenant_id = ra.tenant_id "
                + "WHERE pay.payment_id = ?", ps -> ps.setLong(1, paymentId),
                rs -> new ReceiptContext(rs.getString("tenant_name"), rs.getString("property_name"),
                        date(rs, "period_start"), date(rs, "period_end"), rs.getBigDecimal("balance")));
    }
}
