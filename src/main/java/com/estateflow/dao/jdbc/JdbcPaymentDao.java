package com.estateflow.dao.jdbc;

import com.estateflow.dao.PaymentDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.Payment;
import com.estateflow.model.enums.PaymentMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class JdbcPaymentDao extends BaseJdbcDao implements PaymentDao {

    private static final String COLUMNS = "p.payment_id, p.charge_id, p.amount, p.payment_date, p.payment_method, "
            + "p.reference_no, p.notes, p.voided_at, p.void_reason, p.created_at";

    private static final RowMapper<Payment> MAPPER = rs -> {
        Payment p = new Payment(rs.getLong("charge_id"), rs.getBigDecimal("amount"), date(rs, "payment_date"),
                PaymentMethod.valueOf(rs.getString("payment_method")), rs.getString("reference_no"), rs.getString("notes"));
        p.setPaymentId(rs.getLong("payment_id"));
        p.setVoidedAt(dateTime(rs, "voided_at"));
        p.setVoidReason(rs.getString("void_reason"));
        p.setCreatedAt(dateTime(rs, "created_at"));
        return p;
    };

    public JdbcPaymentDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public Payment insert(Payment p) {
        long id = insertReturningKey("insert payment", "INSERT INTO payment (charge_id, amount, payment_date, "
                + "payment_method, reference_no, notes) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
            ps.setLong(1, p.getChargeId());
            ps.setBigDecimal(2, p.getAmount());
            ps.setObject(3, p.getPaymentDate());
            ps.setString(4, p.getPaymentMethod().name());
            ps.setString(5, p.getReferenceNo());
            ps.setString(6, p.getNotes());
        });
        p.setPaymentId(id);
        return p;
    }

    @Override
    public Optional<Payment> findById(Long paymentId) {
        return queryOne("find payment by id", "SELECT " + COLUMNS + " FROM payment p WHERE p.payment_id = ?",
                ps -> ps.setLong(1, paymentId), MAPPER);
    }

    @Override
    public List<Payment> findByCharge(Long chargeId) {
        return queryList("list payments of charge", "SELECT " + COLUMNS
                + " FROM payment p WHERE p.charge_id = ? ORDER BY p.payment_date, p.payment_id", ps -> ps.setLong(1, chargeId), MAPPER);
    }

    @Override
    public List<Payment> findByDateRange(LocalDate from, LocalDate to) {
        return queryList("list payments by date", "SELECT " + COLUMNS + " FROM payment p WHERE p.payment_date "
                + "BETWEEN ? AND ? ORDER BY p.payment_date, p.payment_id", ps -> {
            ps.setObject(1, from);
            ps.setObject(2, to);
        }, MAPPER);
    }

    @Override
    public int markVoided(Long paymentId, String reason, LocalDateTime at) {
        return executeUpdate("void payment", "UPDATE payment p SET p.voided_at = ?, p.void_reason = ? "
                + "WHERE p.payment_id = ? AND p.voided_at IS NULL", ps -> {
            ps.setObject(1, at);
            ps.setString(2, reason);
            ps.setLong(3, paymentId);
        });
    }

    @Override
    public boolean existsValidForChargesAfter(Long agreementId, LocalDate date) {
        return exists("check payments after termination", "SELECT 1 FROM payment p JOIN rent_charge rc "
                + "ON rc.charge_id = p.charge_id WHERE rc.agreement_id = ? AND rc.period_start > ? AND "
                + SqlFragments.VALID_PAYMENT + " LIMIT 1", ps -> {
            ps.setLong(1, agreementId);
            ps.setObject(2, date);
        });
    }
}
