package com.estateflow.dao.jdbc;

import com.estateflow.dao.RentChargeDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.RentCharge;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class JdbcRentChargeDao extends BaseJdbcDao implements RentChargeDao {

    private static final String BASE = "SELECT rc.charge_id, rc.agreement_id, rc.period_start, rc.period_end, "
            + "rc.due_date, rc.amount_due, rc.cancelled_at";
    private static final String WITH_PAID = BASE + ", " + SqlFragments.AMOUNT_PAID + " AS amount_paid FROM rent_charge rc ";

    private static final RowMapper<RentCharge> MAPPER = rs -> {
        RentCharge c = new RentCharge();
        c.setChargeId(rs.getLong("charge_id"));
        c.setAgreementId(rs.getLong("agreement_id"));
        c.setPeriodStart(date(rs, "period_start"));
        c.setPeriodEnd(date(rs, "period_end"));
        c.setDueDate(date(rs, "due_date"));
        c.setAmountDue(rs.getBigDecimal("amount_due"));
        c.setCancelledAt(dateTime(rs, "cancelled_at"));
        return c;
    };

    private static final RowMapper<RentCharge> MAPPER_WITH_PAID = rs -> {
        RentCharge c = MAPPER.map(rs);
        c.setAmountPaid(rs.getBigDecimal("amount_paid"));
        return c;
    };

    public JdbcRentChargeDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public void insertAll(List<RentCharge> charges) {
        withConnection("insert rent charges", c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO rent_charge (agreement_id, period_start, "
                    + "period_end, due_date, amount_due) VALUES (?, ?, ?, ?, ?)")) {
                for (RentCharge charge : charges) {
                    ps.setLong(1, charge.getAgreementId());
                    ps.setObject(2, charge.getPeriodStart());
                    ps.setObject(3, charge.getPeriodEnd());
                    ps.setObject(4, charge.getDueDate());
                    ps.setBigDecimal(5, charge.getAmountDue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    @Override
    public List<RentCharge> findByAgreement(Long agreementId) {
        return queryList("list charges of agreement", WITH_PAID + "WHERE rc.agreement_id = ? ORDER BY rc.period_start",
                ps -> ps.setLong(1, agreementId), MAPPER_WITH_PAID);
    }

    @Override
    public Optional<RentCharge> findById(Long chargeId) {
        return queryOne("find charge by id", WITH_PAID + "WHERE rc.charge_id = ?", ps -> ps.setLong(1, chargeId), MAPPER_WITH_PAID);
    }

    /**
     * Locks the charge row first (plain columns, no subquery), then sums valid payments in a separate
     * statement. Other recorders block on the row lock, so the sum cannot change underneath us.
     */
    @Override
    public Optional<RentCharge> findByIdForUpdate(Long chargeId) {
        return withConnection("lock charge", c -> {
            Optional<RentCharge> locked = one(c, BASE + " FROM rent_charge rc WHERE rc.charge_id = ? FOR UPDATE",
                    ps -> ps.setLong(1, chargeId), MAPPER);
            if (locked.isPresent()) {
                var paid = one(c, "SELECT COALESCE(SUM(p.amount), 0) AS paid FROM payment p WHERE p.charge_id = ? AND "
                        + SqlFragments.VALID_PAYMENT, ps -> ps.setLong(1, chargeId), rs -> rs.getBigDecimal("paid"));
                locked.get().setAmountPaid(paid.orElse(null));
            }
            return locked;
        });
    }

    @Override
    public int cancelAfter(Long agreementId, LocalDate date, LocalDateTime at) {
        return executeUpdate("cancel charges after termination", "UPDATE rent_charge SET cancelled_at = ? "
                + "WHERE agreement_id = ? AND period_start > ? AND cancelled_at IS NULL", ps -> {
            ps.setObject(1, at);
            ps.setLong(2, agreementId);
            ps.setObject(3, date);
        });
    }
}
