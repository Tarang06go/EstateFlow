package com.estateflow.dao.jdbc;

import com.estateflow.dao.RentCollectionInsightsDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.dto.RentCollectionInsightsReport.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MySQL read-only queries for charge-aligned rent collection insights. */
public final class JdbcRentCollectionInsightsDao extends BaseJdbcDao implements RentCollectionInsightsDao {
    private static final String PAID_TO = "COALESCE((SELECT SUM(p.amount) FROM payment p WHERE p.charge_id = rc.charge_id "
            + "AND p.voided_at IS NULL AND p.payment_date <= ?), 0)";
    private static final String CHARGE_JOINS = " FROM rent_charge rc JOIN rental_agreement ra ON ra.agreement_id = rc.agreement_id "
            + "JOIN property pr ON pr.property_id = ra.property_id JOIN tenant t ON t.tenant_id = ra.tenant_id ";

    public JdbcRentCollectionInsightsDao(DatabaseManager manager) { super(manager); }

    @Override public CollectionTotals totals(LocalDate from, LocalDate to, Long propertyId) {
        String filter = propertyFilter(propertyId);
        Object[] chargeTotals = queryOne("summarize selected rent charges", "SELECT COUNT(*) n, COALESCE(SUM(rc.amount_due),0) billed, "
                + "COALESCE(SUM(LEAST(rc.amount_due," + PAID_TO + ")),0) collected, "
                + "COALESCE(SUM(GREATEST(rc.amount_due-" + PAID_TO + ",0)),0) outstanding " + CHARGE_JOINS
                + "WHERE rc.cancelled_at IS NULL AND rc.due_date BETWEEN ? AND ?" + filter, ps -> {
            ps.setObject(1, to); ps.setObject(2, to); ps.setObject(3, from); ps.setObject(4, to);
            if (propertyId != null) ps.setLong(5, propertyId);
        }, rs -> new Object[]{rs.getInt("n"), rs.getBigDecimal("billed"), rs.getBigDecimal("collected"), rs.getBigDecimal("outstanding")})
                .orElse(new Object[]{0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});

        BigDecimal[] received = queryOne("sum payments received in selected period", "SELECT COALESCE(SUM(p.amount),0) received, "
                + "COALESCE(SUM(CASE WHEN rc.due_date < ? THEN p.amount ELSE 0 END),0) earlier "
                + "FROM payment p JOIN rent_charge rc ON rc.charge_id=p.charge_id JOIN rental_agreement ra ON ra.agreement_id=rc.agreement_id "
                + "WHERE p.voided_at IS NULL AND rc.cancelled_at IS NULL AND p.payment_date BETWEEN ? AND ?" + propertyFilter(propertyId), ps -> {
            ps.setObject(1, from); ps.setObject(2, from); ps.setObject(3, to);
            if (propertyId != null) ps.setLong(4, propertyId);
        }, rs -> new BigDecimal[]{rs.getBigDecimal("received"), rs.getBigDecimal("earlier")})
                .orElse(new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

        BigDecimal billed = (BigDecimal) chargeTotals[1];
        BigDecimal collected = (BigDecimal) chargeTotals[2];
        BigDecimal pct = billed.signum() == 0 ? BigDecimal.ZERO : collected.multiply(BigDecimal.valueOf(100))
                .divide(billed, 2, java.math.RoundingMode.HALF_UP);
        return new CollectionTotals((int) chargeTotals[0], billed, collected, (BigDecimal) chargeTotals[3], pct, received[0], received[1]);
    }

    @Override public List<MonthlyTrend> monthlyTrend(LocalDate from, LocalDate to, Long propertyId) {
        String filter = propertyFilter(propertyId);
        Map<YearMonth, BigDecimal[]> values = new LinkedHashMap<>();
        queryList("summarize collection by charge due month", "SELECT DATE_FORMAT(rc.due_date,'%Y-%m') ym, COUNT(*) n, "
                + "COALESCE(SUM(rc.amount_due),0) billed, COALESCE(SUM(LEAST(rc.amount_due," + PAID_TO + ")),0) collected, "
                + "COALESCE(SUM(GREATEST(rc.amount_due-" + PAID_TO + ",0)),0) outstanding " + CHARGE_JOINS
                + "WHERE rc.cancelled_at IS NULL AND rc.due_date BETWEEN ? AND ?" + filter + " GROUP BY ym ORDER BY ym", ps -> {
            ps.setObject(1, to); ps.setObject(2, to); ps.setObject(3, from); ps.setObject(4, to);
            if (propertyId != null) ps.setLong(5, propertyId);
        }, rs -> values.put(YearMonth.parse(rs.getString("ym")), new BigDecimal[]{rs.getBigDecimal("billed"), rs.getBigDecimal("collected"), rs.getBigDecimal("outstanding")}));
        Map<YearMonth, BigDecimal> receipts = new LinkedHashMap<>();
        queryList("summarize receipts by month", "SELECT DATE_FORMAT(p.payment_date,'%Y-%m') ym, COALESCE(SUM(p.amount),0) received "
                + "FROM payment p JOIN rent_charge rc ON rc.charge_id=p.charge_id JOIN rental_agreement ra ON ra.agreement_id=rc.agreement_id "
                + "WHERE p.voided_at IS NULL AND rc.cancelled_at IS NULL AND p.payment_date BETWEEN ? AND ?" + propertyFilter(propertyId)
                + " GROUP BY ym ORDER BY ym", ps -> bindRange(ps, from, to, propertyId),
                rs -> receipts.put(YearMonth.parse(rs.getString("ym")), rs.getBigDecimal("received")));
        List<MonthlyTrend> result = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            BigDecimal[] row = values.getOrDefault(month, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            result.add(new MonthlyTrend(month, row[0], row[1], row[2], receipts.getOrDefault(month, BigDecimal.ZERO)));
        }
        return List.copyOf(result);
    }

    @Override public List<PropertyBreakdown> byProperty(LocalDate from, LocalDate to, Long propertyId) {
        String filter = propertyFilter(propertyId);
        Map<Long, BigDecimal> receipts = new LinkedHashMap<>();
        queryList("summarize property receipts in period", "SELECT pr.property_id, COALESCE(SUM(p.amount),0) received "
                + "FROM payment p JOIN rent_charge rc ON rc.charge_id=p.charge_id JOIN rental_agreement ra ON ra.agreement_id=rc.agreement_id "
                + "JOIN property pr ON pr.property_id=ra.property_id WHERE p.voided_at IS NULL AND rc.cancelled_at IS NULL "
                + "AND p.payment_date BETWEEN ? AND ?" + propertyFilter(propertyId) + " GROUP BY pr.property_id", ps -> bindRange(ps, from, to, propertyId),
                rs -> receipts.put(rs.getLong("property_id"), rs.getBigDecimal("received")));
        List<PropertyBreakdown> result = queryList("summarize selected charges by property", "SELECT pr.property_id, pr.name, COUNT(*) n, "
                + "COALESCE(SUM(rc.amount_due),0) billed, COALESCE(SUM(LEAST(rc.amount_due," + PAID_TO + ")),0) collected, "
                + "COALESCE(SUM(GREATEST(rc.amount_due-" + PAID_TO + ",0)),0) outstanding " + CHARGE_JOINS
                + "WHERE rc.cancelled_at IS NULL AND rc.due_date BETWEEN ? AND ?" + filter + " GROUP BY pr.property_id,pr.name ORDER BY outstanding DESC,pr.name", ps -> {
            ps.setObject(1, to); ps.setObject(2, to); ps.setObject(3, from); ps.setObject(4, to);
            if (propertyId != null) ps.setLong(5, propertyId);
        }, rs -> new PropertyBreakdown(rs.getLong("property_id"), rs.getString("name"), rs.getInt("n"),
                rs.getBigDecimal("billed"), rs.getBigDecimal("collected"), rs.getBigDecimal("outstanding"),
                receipts.getOrDefault(rs.getLong("property_id"), BigDecimal.ZERO)));
        return List.copyOf(result);
    }

    @Override public List<TenantBreakdown> byTenant(LocalDate from, LocalDate to, Long propertyId) {
        String filter = propertyFilter(propertyId);
        Map<String, BigDecimal> receipts = new LinkedHashMap<>();
        queryList("summarize tenant receipts in period", "SELECT t.tenant_id,pr.property_id,COALESCE(SUM(p.amount),0) received "
                + "FROM payment p JOIN rent_charge rc ON rc.charge_id=p.charge_id JOIN rental_agreement ra ON ra.agreement_id=rc.agreement_id "
                + "JOIN property pr ON pr.property_id=ra.property_id JOIN tenant t ON t.tenant_id=ra.tenant_id "
                + "WHERE p.voided_at IS NULL AND rc.cancelled_at IS NULL AND p.payment_date BETWEEN ? AND ?" + propertyFilter(propertyId)
                + " GROUP BY t.tenant_id,pr.property_id", ps -> bindRange(ps, from, to, propertyId),
                rs -> receipts.put(rs.getLong("tenant_id") + ":" + rs.getLong("property_id"), rs.getBigDecimal("received")));
        return queryList("summarize selected charges by tenant", "SELECT t.tenant_id,t.full_name,pr.property_id,pr.name,COUNT(*) n, "
                + "COALESCE(SUM(rc.amount_due),0) billed, COALESCE(SUM(LEAST(rc.amount_due," + PAID_TO + ")),0) collected, "
                + "COALESCE(SUM(GREATEST(rc.amount_due-" + PAID_TO + ",0)),0) outstanding " + CHARGE_JOINS
                + "WHERE rc.cancelled_at IS NULL AND rc.due_date BETWEEN ? AND ?" + filter
                + " GROUP BY t.tenant_id,t.full_name,pr.property_id,pr.name ORDER BY outstanding DESC,t.full_name", ps -> {
            ps.setObject(1, to); ps.setObject(2, to); ps.setObject(3, from); ps.setObject(4, to);
            if (propertyId != null) ps.setLong(5, propertyId);
        }, rs -> new TenantBreakdown(rs.getLong("tenant_id"), rs.getString("full_name"), rs.getString("name"), rs.getInt("n"),
                rs.getBigDecimal("billed"), rs.getBigDecimal("collected"), rs.getBigDecimal("outstanding"),
                receipts.getOrDefault(rs.getLong("tenant_id") + ":" + rs.getLong("property_id"), BigDecimal.ZERO)));
    }

    @Override public List<ArrearsBand> arrears(LocalDate asOf, Long propertyId) {
        String chargeRows = "SELECT rc.charge_id,rc.due_date,GREATEST(rc.amount_due," + "0) amount_due," + PAID_TO + " paid " + CHARGE_JOINS
                + "WHERE rc.cancelled_at IS NULL AND rc.due_date < ?" + propertyFilter(propertyId);
        return queryList("group current arrears by age", "SELECT CASE WHEN days_overdue<=30 THEN '1-30 days' "
                + "WHEN days_overdue<=60 THEN '31-60 days' WHEN days_overdue<=90 THEN '61-90 days' ELSE 'Over 90 days' END age_band, "
                + "COUNT(*) n,COALESCE(SUM(balance),0) balance FROM (SELECT DATEDIFF(?,x.due_date) days_overdue, "
                + "GREATEST(x.amount_due-x.paid,0) balance FROM (" + chargeRows + ") x) aged WHERE balance>0 GROUP BY age_band "
                + "ORDER BY CASE age_band WHEN '1-30 days' THEN 1 WHEN '31-60 days' THEN 2 WHEN '61-90 days' THEN 3 ELSE 4 END", ps -> {
            ps.setObject(1, asOf); ps.setObject(2, asOf); ps.setObject(3, asOf);
            if (propertyId != null) ps.setLong(4, propertyId);
        }, rs -> new ArrearsBand(rs.getString("age_band"), rs.getInt("n"), rs.getBigDecimal("balance")));
    }

    @Override public List<ChargeFollowUp> followUp(LocalDate asOf, Long propertyId, boolean includeTenantName) {
        String filter = propertyFilter(propertyId);
        String sql = "SELECT x.charge_id,x.property_name,x.tenant_name,x.due_date,x.days_overdue,x.amount_due,x.paid,GREATEST(x.amount_due-x.paid,0) balance "
                + "FROM (SELECT rc.charge_id,pr.name property_name,t.full_name tenant_name,rc.due_date,DATEDIFF(?,rc.due_date) days_overdue,rc.amount_due," + PAID_TO + " paid "
                + CHARGE_JOINS + "WHERE rc.cancelled_at IS NULL AND rc.due_date<?" + filter + ") x "
                + "WHERE GREATEST(x.amount_due-x.paid,0)>0 ORDER BY x.days_overdue DESC,x.due_date,x.charge_id";
        return queryList("list charges requiring rent follow-up", sql, ps -> {
            ps.setObject(1, asOf); ps.setObject(2, asOf); ps.setObject(3, asOf);
            if (propertyId != null) ps.setLong(4, propertyId);
        }, rs -> new ChargeFollowUp(rs.getLong("charge_id"), rs.getString("property_name"), includeTenantName ? rs.getString("tenant_name") : null,
                date(rs, "due_date"), rs.getLong("days_overdue"), rs.getBigDecimal("amount_due"), rs.getBigDecimal("paid"), rs.getBigDecimal("balance")));
    }

    @Override public List<LatePaymentBehavior> paymentBehavior(LocalDate from, LocalDate to, Long propertyId, boolean includeTenantName) {
        String filter = propertyFilter(propertyId);
        String paidOff = "(SELECT MIN(p2.payment_date) FROM payment p2 WHERE p2.charge_id=rc.charge_id AND p2.voided_at IS NULL "
                + "AND (SELECT COALESCE(SUM(p3.amount),0) FROM payment p3 WHERE p3.charge_id=rc.charge_id AND p3.voided_at IS NULL "
                + "AND p3.payment_date<=p2.payment_date)>=rc.amount_due)";
        String sql = "SELECT x.tenant_id,x.tenant_name,x.property_name,COUNT(*) settled,SUM(CASE WHEN x.settled_on>x.due_date THEN 1 ELSE 0 END) late "
                + "FROM (SELECT t.tenant_id,t.full_name tenant_name,pr.name property_name,rc.due_date," + paidOff + " settled_on " + CHARGE_JOINS
                + "WHERE rc.cancelled_at IS NULL AND rc.due_date BETWEEN ? AND ?" + filter + ") x WHERE x.settled_on IS NOT NULL "
                + "GROUP BY x.tenant_id,x.tenant_name,x.property_name ORDER BY late DESC,x.tenant_name";
        return queryList("summarize late rent payment behavior", sql, ps -> {
            // paidOff is fully correlated to rc and has no placeholders. Only the due-date range
            // (and optional property filter) belong to this query; binding an extra date here
            // caused Connector/J S1009 before MySQL could execute the query.
            ps.setObject(1, from);
            ps.setObject(2, to);
            if (propertyId != null) ps.setLong(3, propertyId);
        }, rs -> {
            int settled = rs.getInt("settled"), late = rs.getInt("late");
            boolean enough = settled >= 3;
            return new LatePaymentBehavior(rs.getLong("tenant_id"), includeTenantName ? rs.getString("tenant_name") : null,
                    rs.getString("property_name"), settled, late, enough, enough && late >= 3);
        });
    }

    private static String propertyFilter(Long propertyId) { return propertyId == null ? "" : " AND ra.property_id=?"; }
    private static void bindRange(java.sql.PreparedStatement ps, LocalDate from, LocalDate to, Long propertyId) throws java.sql.SQLException {
        ps.setObject(1, from); ps.setObject(2, to); if (propertyId != null) ps.setLong(3, propertyId);
    }
}
