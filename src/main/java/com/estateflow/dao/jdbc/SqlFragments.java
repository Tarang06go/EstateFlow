package com.estateflow.dao.jdbc;

/**
 * Shared SQL definitions (Section 6.4). Table aliases are fixed by convention:
 * ra = rental_agreement, rc = rent_charge, p = payment.
 */
public final class SqlFragments {
    public static final String VALID_PAYMENT = "p.voided_at IS NULL";
    public static final String LIVE_CHARGE = "rc.cancelled_at IS NULL";
    public static final String EFFECTIVE_END = "ra.effective_end_date";
    /** Agreement occupies a date: start_date <= ? AND effective_end_date >= ? (bind date twice). */
    public static final String OCCUPIES = "ra.start_date <= ? AND " + EFFECTIVE_END + " >= ?";
    /** Sum of valid payments of the charge aliased as rc. */
    public static final String AMOUNT_PAID = "COALESCE((SELECT SUM(p.amount) FROM payment p "
            + "WHERE p.charge_id = rc.charge_id AND " + VALID_PAYMENT + "), 0)";

    private SqlFragments() {
    }
}
