package com.estateflow.dao.jdbc;

import com.estateflow.dao.LeaseDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.dto.LeaseAttentionRow;
import com.estateflow.model.LeaseChange;

import java.sql.Types;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class JdbcLeaseDao extends BaseJdbcDao implements LeaseDao {

    private static final RowMapper<LeaseChange> CHANGE_MAPPER = rs -> new LeaseChange(rs.getLong("change_id"),
            rs.getLong("agreement_id"), rs.getString("change_type"), longOrNull(rs, "related_agreement_id"),
            date(rs, "old_start_date"), date(rs, "old_end_date"), rs.getBigDecimal("old_monthly_rent"),
            date(rs, "new_start_date"), date(rs, "new_end_date"), rs.getBigDecimal("new_monthly_rent"),
            longOrNull(rs, "actor_user_id"), rs.getString("actor_username"), rs.getString("note"), dateTime(rs, "created_at"));

    public JdbcLeaseDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public boolean supportsRenewal() {
        return exists("check renewal support", "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'rental_agreement' AND column_name = 'renewed_from_agreement_id' LIMIT 1", Binder.NONE)
                && exists("check lease_change table", "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name = 'lease_change' LIMIT 1", Binder.NONE);
    }

    @Override
    public Optional<Long> findSuccessor(long agreementId) {
        return queryOne("find renewal of agreement", "SELECT agreement_id FROM rental_agreement WHERE renewed_from_agreement_id = ?",
                ps -> ps.setLong(1, agreementId), rs -> rs.getLong("agreement_id"));
    }

    @Override
    public int setRenewedFrom(long newAgreementId, long oldAgreementId) {
        return executeUpdate("link renewal", "UPDATE rental_agreement SET renewed_from_agreement_id = ? WHERE agreement_id = ?", ps -> {
            ps.setLong(1, oldAgreementId);
            ps.setLong(2, newAgreementId);
        });
    }

    @Override
    public int extendTerm(long agreementId, int newDurationMonths, LocalDate newEndDate) {
        return executeUpdate("extend agreement", "UPDATE rental_agreement SET duration_months = ?, end_date = ? "
                + "WHERE agreement_id = ? AND status = 'ACTIVE'", ps -> {
            ps.setInt(1, newDurationMonths);
            ps.setObject(2, newEndDate);
            ps.setLong(3, agreementId);
        });
    }

    @Override
    public void insertChange(LeaseChange c) {
        insertReturningKey("insert lease change", "INSERT INTO lease_change (agreement_id, change_type, related_agreement_id, "
                + "old_start_date, old_end_date, old_monthly_rent, new_start_date, new_end_date, new_monthly_rent, actor_user_id, "
                + "actor_username, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setLong(1, c.agreementId());
            ps.setString(2, c.changeType());
            if (c.relatedAgreementId() == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, c.relatedAgreementId());
            }
            ps.setObject(4, c.oldStartDate());
            ps.setObject(5, c.oldEndDate());
            ps.setBigDecimal(6, c.oldMonthlyRent());
            ps.setObject(7, c.newStartDate());
            ps.setObject(8, c.newEndDate());
            ps.setBigDecimal(9, c.newMonthlyRent());
            if (c.actorUserId() == null) {
                ps.setNull(10, Types.BIGINT);
            } else {
                ps.setLong(10, c.actorUserId());
            }
            ps.setString(11, c.actorUsername());
            ps.setString(12, c.note());
        });
    }

    @Override
    public List<LeaseChange> findChanges(long agreementId) {
        return queryList("list lease changes", "SELECT change_id, agreement_id, change_type, related_agreement_id, old_start_date, "
                + "old_end_date, old_monthly_rent, new_start_date, new_end_date, new_monthly_rent, actor_user_id, actor_username, "
                + "note, created_at FROM lease_change WHERE agreement_id = ? OR related_agreement_id = ? ORDER BY change_id", ps -> {
            ps.setLong(1, agreementId);
            ps.setLong(2, agreementId);
        }, CHANGE_MAPPER);
    }

    @Override
    public List<LeaseAttentionRow> findEndedNeedingAttention(LocalDate today) {
        return queryList("list ended leases needing attention", "SELECT q.* FROM (SELECT ra.agreement_id, pr.name AS property_name, "
                + "t.full_name AS tenant_name, ra.effective_end_date AS effective_end, ra.status AS agreement_status, "
                + "COALESCE((SELECT SUM(c.amount_due) FROM rent_charge c WHERE c.agreement_id = ra.agreement_id AND c.cancelled_at IS NULL), 0) "
                + "- COALESCE((SELECT SUM(p.amount) FROM payment p JOIN rent_charge c2 ON c2.charge_id = p.charge_id "
                + "WHERE c2.agreement_id = ra.agreement_id AND c2.cancelled_at IS NULL AND p.voided_at IS NULL), 0) AS rent_outstanding, "
                + "COALESCE((SELECT SUM(CASE WHEN x.txn_type IN ('COLLECTION', 'TRANSFER_IN') THEN x.amount ELSE -x.amount END) "
                + "FROM security_deposit d JOIN deposit_transaction x ON x.deposit_id = d.deposit_id AND x.voided_at IS NULL "
                + "WHERE d.agreement_id = ra.agreement_id), 0) AS deposit_balance "
                + "FROM rental_agreement ra JOIN property pr ON pr.property_id = ra.property_id JOIN tenant t ON t.tenant_id = ra.tenant_id "
                + "WHERE ra.effective_end_date < ? AND NOT EXISTS (SELECT 1 FROM rental_agreement r2 WHERE r2.renewed_from_agreement_id = ra.agreement_id)) q "
                + "WHERE q.rent_outstanding > 0 OR q.deposit_balance > 0 ORDER BY q.effective_end, q.agreement_id",
                ps -> ps.setObject(1, today), rs -> {
                    LocalDate end = date(rs, "effective_end");
                    return new LeaseAttentionRow(rs.getLong("agreement_id"), rs.getString("property_name"), rs.getString("tenant_name"), end,
                            ChronoUnit.DAYS.between(end, today), "TERMINATED".equals(rs.getString("agreement_status")),
                            rs.getBigDecimal("rent_outstanding"), rs.getBigDecimal("deposit_balance"));
                });
    }
}
