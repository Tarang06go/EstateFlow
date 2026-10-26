package com.estateflow.dao.jdbc;

import com.estateflow.dao.DepositDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.dto.DepositActivityRow;
import com.estateflow.dto.DepositRow;
import com.estateflow.dto.DepositTotals;
import com.estateflow.model.DepositTransaction;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.model.enums.DepositType;
import com.estateflow.model.enums.PaymentMethod;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcDepositDao extends BaseJdbcDao implements DepositDao {

    private static final String TXN_COLUMNS = "x.txn_id, x.deposit_id, x.txn_type, x.amount, x.txn_date, x.payment_method, "
            + "x.reference_no, x.reason, x.notes, x.related_agreement_id, x.created_at, x.voided_at, x.void_reason";

    private static final RowMapper<DepositTransaction> TXN_MAPPER = rs -> {
        String method = rs.getString("payment_method");
        return new DepositTransaction(rs.getLong("txn_id"), rs.getLong("deposit_id"), DepositType.valueOf(rs.getString("txn_type")),
                rs.getBigDecimal("amount"), date(rs, "txn_date"), method == null ? null : PaymentMethod.valueOf(method),
                rs.getString("reference_no"), rs.getString("reason"), rs.getString("notes"),
                longOrNull(rs, "related_agreement_id"), dateTime(rs, "created_at"), dateTime(rs, "voided_at"),
                rs.getString("void_reason"));
    };

    private static final String SUMS = "COALESCE(SUM(CASE WHEN x.txn_type = 'COLLECTION' THEN x.amount END), 0) AS collected, "
            + "COALESCE(SUM(CASE WHEN x.txn_type = 'TRANSFER_IN' THEN x.amount END), 0) AS transferred_in, "
            + "COALESCE(SUM(CASE WHEN x.txn_type = 'DEDUCTION' THEN x.amount END), 0) AS deducted, "
            + "COALESCE(SUM(CASE WHEN x.txn_type = 'REFUND' THEN x.amount END), 0) AS refunded, "
            + "COALESCE(SUM(CASE WHEN x.txn_type = 'TRANSFER_OUT' THEN x.amount END), 0) AS transferred_out";

    private static final String ROW_SELECT = "SELECT ra.agreement_id, ra.property_id, pr.name AS property_name, ra.tenant_id, "
            + "t.full_name AS tenant_name, ra.security_deposit AS required, ra.start_date, ra.effective_end_date AS effective_end, "
            + "ra.status AS agreement_status, d.deposit_id, "
            + "(SELECT COUNT(*) FROM rental_agreement r2 WHERE r2.renewed_from_agreement_id = ra.agreement_id) > 0 AS renewed, "
            + "MIN(CASE WHEN x.txn_type = 'COLLECTION' THEN x.txn_date END) AS first_collection, " + SUMS + " "
            + "FROM rental_agreement ra JOIN property pr ON pr.property_id = ra.property_id "
            + "JOIN tenant t ON t.tenant_id = ra.tenant_id "
            + "LEFT JOIN security_deposit d ON d.agreement_id = ra.agreement_id "
            + "LEFT JOIN deposit_transaction x ON x.deposit_id = d.deposit_id AND x.voided_at IS NULL ";

    private static final String ROW_GROUP = "GROUP BY ra.agreement_id, ra.property_id, pr.name, ra.tenant_id, t.full_name, "
            + "ra.security_deposit, ra.start_date, ra.effective_end_date, ra.status, d.deposit_id ";

    private static final RowMapper<DepositRow> ROW_MAPPER = rs -> new DepositRow(longOrNull(rs, "deposit_id"),
            rs.getLong("agreement_id"), rs.getLong("property_id"), rs.getString("property_name"), rs.getLong("tenant_id"),
            rs.getString("tenant_name"), rs.getBigDecimal("required"), totals(rs), date(rs, "start_date"),
            date(rs, "effective_end"), AgreementStatus.valueOf(rs.getString("agreement_status")),
            rs.getBoolean("renewed"), date(rs, "first_collection"));

    public JdbcDepositDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    private static DepositTotals totals(java.sql.ResultSet rs) throws SQLException {
        return new DepositTotals(rs.getBigDecimal("collected"), rs.getBigDecimal("transferred_in"), rs.getBigDecimal("deducted"),
                rs.getBigDecimal("refunded"), rs.getBigDecimal("transferred_out"));
    }

    @Override
    public boolean isAvailable() {
        return exists("check deposit tables", "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name = 'deposit_transaction' LIMIT 1", Binder.NONE);
    }

    @Override
    public Optional<Long> findDepositId(long agreementId) {
        return queryOne("find deposit", "SELECT deposit_id FROM security_deposit WHERE agreement_id = ?",
                ps -> ps.setLong(1, agreementId), rs -> rs.getLong("deposit_id"));
    }

    @Override
    public long insertDeposit(long agreementId) {
        return insertReturningKey("insert deposit", "INSERT INTO security_deposit (agreement_id) VALUES (?)",
                ps -> ps.setLong(1, agreementId));
    }

    @Override
    public boolean lockDeposit(long depositId) {
        return queryOne("lock deposit", "SELECT deposit_id FROM security_deposit WHERE deposit_id = ? FOR UPDATE",
                ps -> ps.setLong(1, depositId), rs -> rs.getLong("deposit_id")).isPresent();
    }

    @Override
    public DepositTotals totals(long depositId) {
        return queryOne("sum deposit", "SELECT " + SUMS + " FROM deposit_transaction x WHERE x.deposit_id = ? AND x.voided_at IS NULL",
                ps -> ps.setLong(1, depositId), JdbcDepositDao::totals).orElse(DepositTotals.EMPTY);
    }

    @Override
    public Optional<LocalDate> firstCollectionDate(long depositId) {
        return queryOne("first collection date", "SELECT MIN(x.txn_date) AS first_date FROM deposit_transaction x "
                + "WHERE x.deposit_id = ? AND x.txn_type = 'COLLECTION' AND x.voided_at IS NULL",
                ps -> ps.setLong(1, depositId), rs -> date(rs, "first_date")).filter(d -> d != null);
    }

    @Override
    public long insertTransaction(DepositTransaction t) {
        return insertReturningKey("insert deposit transaction", "INSERT INTO deposit_transaction (deposit_id, txn_type, amount, "
                + "txn_date, payment_method, reference_no, reason, notes, related_agreement_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setLong(1, t.depositId());
            ps.setString(2, t.type().name());
            ps.setBigDecimal(3, t.amount());
            ps.setObject(4, t.date());
            ps.setString(5, t.method() == null ? null : t.method().name());
            ps.setString(6, t.referenceNo());
            ps.setString(7, t.reason());
            ps.setString(8, t.notes());
            if (t.relatedAgreementId() == null) {
                ps.setNull(9, Types.BIGINT);
            } else {
                ps.setLong(9, t.relatedAgreementId());
            }
        });
    }

    @Override
    public Optional<DepositTransaction> findTransaction(long txnId) {
        return queryOne("find deposit transaction", "SELECT " + TXN_COLUMNS + " FROM deposit_transaction x WHERE x.txn_id = ?",
                ps -> ps.setLong(1, txnId), TXN_MAPPER);
    }

    @Override
    public int voidTransaction(long txnId, String reason, LocalDateTime voidedAtUtc) {
        return executeUpdate("void deposit transaction", "UPDATE deposit_transaction SET voided_at = ?, void_reason = ? "
                + "WHERE txn_id = ? AND voided_at IS NULL", ps -> {
            ps.setObject(1, voidedAtUtc);
            ps.setString(2, reason);
            ps.setLong(3, txnId);
        });
    }

    @Override
    public List<DepositTransaction> transactions(long depositId) {
        return queryList("list deposit transactions", "SELECT " + TXN_COLUMNS + " FROM deposit_transaction x WHERE x.deposit_id = ? "
                + "ORDER BY x.txn_date, x.txn_id", ps -> ps.setLong(1, depositId), TXN_MAPPER);
    }

    @Override
    public List<DepositRow> rows(Long propertyIdOrNull, Long tenantIdOrNull, LocalDate today) {
        StringBuilder where = new StringBuilder("WHERE (d.deposit_id IS NOT NULL OR (ra.security_deposit > 0 AND ra.effective_end_date >= ?)) ");
        List<Object> params = new ArrayList<>();
        params.add(today);
        if (propertyIdOrNull != null) {
            where.append("AND ra.property_id = ? ");
            params.add(propertyIdOrNull);
        }
        if (tenantIdOrNull != null) {
            where.append("AND ra.tenant_id = ? ");
            params.add(tenantIdOrNull);
        }
        return queryList("list deposits", ROW_SELECT + where + ROW_GROUP + "ORDER BY pr.name, ra.agreement_id",
                ps -> bindAll(ps, params), ROW_MAPPER);
    }

    @Override
    public Optional<DepositRow> rowForAgreement(long agreementId, LocalDate today) {
        return queryOne("find deposit position", ROW_SELECT + "WHERE ra.agreement_id = ? " + ROW_GROUP,
                ps -> ps.setLong(1, agreementId), ROW_MAPPER);
    }

    @Override
    public List<DepositActivityRow> activity(LocalDate from, LocalDate to, Long propertyIdOrNull, Long tenantIdOrNull) {
        StringBuilder sql = new StringBuilder("SELECT " + TXN_COLUMNS + ", d.agreement_id, pr.name AS property_name, t.full_name AS tenant_name "
                + "FROM deposit_transaction x JOIN security_deposit d ON d.deposit_id = x.deposit_id "
                + "JOIN rental_agreement ra ON ra.agreement_id = d.agreement_id JOIN property pr ON pr.property_id = ra.property_id "
                + "JOIN tenant t ON t.tenant_id = ra.tenant_id WHERE x.voided_at IS NULL "
                + "AND x.txn_type IN ('COLLECTION', 'DEDUCTION', 'REFUND') AND x.txn_date BETWEEN ? AND ? ");
        List<Object> params = new ArrayList<>();
        params.add(from);
        params.add(to);
        if (propertyIdOrNull != null) {
            sql.append("AND ra.property_id = ? ");
            params.add(propertyIdOrNull);
        }
        if (tenantIdOrNull != null) {
            sql.append("AND ra.tenant_id = ? ");
            params.add(tenantIdOrNull);
        }
        sql.append("ORDER BY x.txn_date DESC, x.txn_id DESC");
        return queryList("list deposit activity", sql.toString(), ps -> bindAll(ps, params),
                rs -> new DepositActivityRow(TXN_MAPPER.map(rs), rs.getLong("agreement_id"), rs.getString("property_name"),
                        rs.getString("tenant_name")));
    }

    private static void bindAll(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }
}
