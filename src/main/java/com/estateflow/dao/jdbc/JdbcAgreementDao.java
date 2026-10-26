package com.estateflow.dao.jdbc;

import com.estateflow.dao.AgreementDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.dto.AgreementListRow;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.model.RentalAgreement;
import com.estateflow.model.enums.AgreementFilter;
import com.estateflow.model.enums.AgreementStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** All date logic uses the generated effective_end_date column; it is never written. */
public class JdbcAgreementDao extends BaseJdbcDao implements AgreementDao {

    private static final String COLUMNS = "ra.agreement_id, ra.property_id, ra.tenant_id, ra.start_date, "
            + "ra.duration_months, ra.end_date, ra.monthly_rent, ra.security_deposit, ra.status, "
            + "ra.termination_date, ra.notes, ra.created_at";

    private static final String NOT_RENEWED = "AND NOT EXISTS (SELECT 1 FROM rental_agreement r2 WHERE r2.renewed_from_agreement_id = ra.agreement_id) ";
    private static final String RENEWAL_COLUMNS = ", ra.renewed_from_agreement_id, (SELECT r2.agreement_id FROM rental_agreement r2 "
            + "WHERE r2.renewed_from_agreement_id = ra.agreement_id) AS renewed_by";

    private volatile Boolean renewalColumn;

    /** True once the v1.5 migration added the renewal link; until then the old queries are used unchanged. */
    private boolean renewalSupported() {
        Boolean known = renewalColumn;
        if (known == null) {
            known = exists("check renewal column", "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() "
                    + "AND table_name = 'rental_agreement' AND column_name = 'renewed_from_agreement_id' LIMIT 1", Binder.NONE);
            renewalColumn = known;
        }
        return known;
    }

    private String columns() {
        return COLUMNS + (renewalSupported() ? RENEWAL_COLUMNS : "");
    }

    private String notRenewed() {
        return renewalSupported() ? NOT_RENEWED : "";
    }

    private static Long optionalLong(java.sql.ResultSet rs, String label) throws java.sql.SQLException {
        java.sql.ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (label.equalsIgnoreCase(md.getColumnLabel(i))) {
                long value = rs.getLong(i);
                return rs.wasNull() ? null : value;
            }
        }
        return null;
    }

    private static final RowMapper<RentalAgreement> MAPPER = rs -> {
        RentalAgreement a = new RentalAgreement();
        a.setAgreementId(rs.getLong("agreement_id"));
        a.setPropertyId(rs.getLong("property_id"));
        a.setTenantId(rs.getLong("tenant_id"));
        a.setStartDate(date(rs, "start_date"));
        a.setDurationMonths(rs.getInt("duration_months"));
        a.setEndDate(date(rs, "end_date"));
        a.setMonthlyRent(rs.getBigDecimal("monthly_rent"));
        a.setSecurityDeposit(rs.getBigDecimal("security_deposit"));
        a.setStatus(AgreementStatus.valueOf(rs.getString("status")));
        a.setTerminationDate(date(rs, "termination_date"));
        a.setNotes(rs.getString("notes"));
        a.setCreatedAt(dateTime(rs, "created_at"));
        a.setRenewedFromAgreementId(optionalLong(rs, "renewed_from_agreement_id"));
        a.setRenewedByAgreementId(optionalLong(rs, "renewed_by"));
        return a;
    };

    public JdbcAgreementDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public RentalAgreement insert(RentalAgreement a) {
        long id = insertReturningKey("insert agreement", "INSERT INTO rental_agreement (property_id, tenant_id, "
                + "start_date, duration_months, end_date, monthly_rent, security_deposit, status, termination_date, notes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setLong(1, a.getPropertyId());
            ps.setLong(2, a.getTenantId());
            ps.setObject(3, a.getStartDate());
            ps.setInt(4, a.getDurationMonths());
            ps.setObject(5, a.getEndDate());
            ps.setBigDecimal(6, a.getMonthlyRent());
            ps.setBigDecimal(7, a.getSecurityDeposit());
            ps.setString(8, a.getStatus().name());
            ps.setObject(9, a.getTerminationDate());
            ps.setString(10, a.getNotes());
        });
        a.setAgreementId(id);
        return a;
    }

    /** Only status, termination_date and notes can change (BR-15, BR-16). */
    @Override
    public int update(RentalAgreement a) {
        return executeUpdate("update agreement", "UPDATE rental_agreement SET status = ?, termination_date = ?, "
                + "notes = ? WHERE agreement_id = ?", ps -> {
            ps.setString(1, a.getStatus().name());
            ps.setObject(2, a.getTerminationDate());
            ps.setString(3, a.getNotes());
            ps.setLong(4, a.getAgreementId());
        });
    }

    @Override
    public Optional<RentalAgreement> findById(Long id) {
        return queryOne("find agreement by id", "SELECT " + columns() + " FROM rental_agreement ra WHERE ra.agreement_id = ?",
                ps -> ps.setLong(1, id), MAPPER);
    }

    @Override
    public List<RentalAgreement> findAll() {
        return queryList("list agreements", "SELECT " + columns() + " FROM rental_agreement ra ORDER BY ra.start_date DESC",
                Binder.NONE, MAPPER);
    }

    @Override
    public List<RentalAgreement> findByProperty(Long propertyId) {
        return queryList("list agreements of property", "SELECT " + columns()
                + " FROM rental_agreement ra WHERE ra.property_id = ? ORDER BY ra.start_date", ps -> ps.setLong(1, propertyId), MAPPER);
    }

    @Override
    public List<RentalAgreement> findByTenant(Long tenantId) {
        return queryList("list agreements of tenant", "SELECT " + columns()
                + " FROM rental_agreement ra WHERE ra.tenant_id = ? ORDER BY ra.start_date", ps -> ps.setLong(1, tenantId), MAPPER);
    }

    @Override
    public List<RentalAgreement> findOverlapping(Long propertyId, LocalDate from, LocalDate to) {
        return queryList("find overlapping agreements", "SELECT " + columns() + " FROM rental_agreement ra "
                + "WHERE ra.property_id = ? AND ra.start_date <= ? AND " + SqlFragments.EFFECTIVE_END + " >= ? "
                + "ORDER BY ra.start_date", ps -> {
            ps.setLong(1, propertyId);
            ps.setObject(2, to);
            ps.setObject(3, from);
        }, MAPPER);
    }

    @Override
    public List<ExpiringLeaseRow> findEndingBetween(LocalDate from, LocalDate to) {
        return queryList("list ending agreements", "SELECT ra.agreement_id, pr.name AS property_name, "
                + "t.full_name AS tenant_name, " + SqlFragments.EFFECTIVE_END + " AS effective_end, ra.monthly_rent, "
                + "ra.status FROM rental_agreement ra JOIN property pr ON pr.property_id = ra.property_id "
                + "JOIN tenant t ON t.tenant_id = ra.tenant_id WHERE " + SqlFragments.EFFECTIVE_END
                + " BETWEEN ? AND ? " + notRenewed() + "ORDER BY " + SqlFragments.EFFECTIVE_END + ", ra.agreement_id", ps -> {
            ps.setObject(1, from);
            ps.setObject(2, to);
        }, rs -> {
            LocalDate end = date(rs, "effective_end");
            return new ExpiringLeaseRow(rs.getLong("agreement_id"), rs.getString("property_name"),
                    rs.getString("tenant_name"), end, ChronoUnit.DAYS.between(from, end),
                    rs.getBigDecimal("monthly_rent"), AgreementStatus.TERMINATED.name().equals(rs.getString("status")));
        });
    }

    @Override
    public boolean existsOpenForTenant(Long tenantId, LocalDate today) {
        return exists("check open agreement of tenant", "SELECT 1 FROM rental_agreement ra WHERE ra.tenant_id = ? AND "
                + SqlFragments.EFFECTIVE_END + " >= ? LIMIT 1", ps -> {
            ps.setLong(1, tenantId);
            ps.setObject(2, today);
        });
    }

    @Override
    public boolean existsOpenForProperty(Long propertyId, LocalDate today) {
        return exists("check open agreement of property", "SELECT 1 FROM rental_agreement ra WHERE ra.property_id = ? AND "
                + SqlFragments.EFFECTIVE_END + " >= ? LIMIT 1", ps -> {
            ps.setLong(1, propertyId);
            ps.setObject(2, today);
        });
    }

    @Override
    public boolean existsForPropertyAndTenant(Long propertyId, Long tenantId) {
        return exists("check agreement history", "SELECT 1 FROM rental_agreement ra WHERE ra.property_id = ? "
                + "AND ra.tenant_id = ? LIMIT 1", ps -> {
            ps.setLong(1, propertyId);
            ps.setLong(2, tenantId);
        });
    }

    private static final String ROW_SELECT = "SELECT ra.agreement_id, ra.property_id, pr.name AS property_name, "
            + "ra.tenant_id, t.full_name AS tenant_name, ra.start_date, ra.end_date, "
            + SqlFragments.EFFECTIVE_END + " AS effective_end, ra.monthly_rent, ra.status, ra.termination_date "
            + "FROM rental_agreement ra JOIN property pr ON pr.property_id = ra.property_id "
            + "JOIN tenant t ON t.tenant_id = ra.tenant_id ";

    private static final RowMapper<AgreementListRow> ROW_MAPPER = rs -> new AgreementListRow(rs.getLong("agreement_id"),
            rs.getLong("property_id"), rs.getString("property_name"), rs.getLong("tenant_id"), rs.getString("tenant_name"),
            date(rs, "start_date"), date(rs, "end_date"), date(rs, "effective_end"), rs.getBigDecimal("monthly_rent"),
            AgreementStatus.valueOf(rs.getString("status")), date(rs, "termination_date"));

    @Override
    public List<AgreementListRow> findAllRows() {
        return queryList("list agreement rows", ROW_SELECT + "ORDER BY ra.start_date DESC, ra.agreement_id DESC",
                Binder.NONE, ROW_MAPPER);
    }

    @Override
    public List<AgreementListRow> findRowsByProperty(Long propertyId) {
        return queryList("list agreement rows of property", ROW_SELECT + "WHERE ra.property_id = ? "
                + "ORDER BY ra.start_date DESC, ra.agreement_id DESC", ps -> ps.setLong(1, propertyId), ROW_MAPPER);
    }

    @Override
    public List<AgreementListRow> findRowsByTenant(Long tenantId) {
        return queryList("list agreement rows of tenant", ROW_SELECT + "WHERE ra.tenant_id = ? "
                + "ORDER BY ra.start_date DESC, ra.agreement_id DESC", ps -> ps.setLong(1, tenantId), ROW_MAPPER);
    }

    @Override
    public List<AgreementListRow> searchRows(String text, AgreementFilter filter, LocalDate today, int limit) {
        String needle = text == null ? "" : text.trim();
        StringBuilder where = new StringBuilder("WHERE 1 = 1 ");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (!needle.isEmpty()) {
            where.append("AND (pr.name LIKE ? OR t.full_name LIKE ? OR CAST(ra.agreement_id AS CHAR) = ?) ");
            params.add(like(needle));
            params.add(like(needle));
            params.add(needle.startsWith("#") ? needle.substring(1) : needle);
        }
        switch (filter == null ? AgreementFilter.ALL : filter) {
            case ACTIVE -> {
                where.append("AND " + SqlFragments.OCCUPIES + " ");
                params.add(today);
                params.add(today);
            }
            case UPCOMING -> {
                where.append("AND ra.start_date > ? ");
                params.add(today);
            }
            case EXPIRED -> {
                where.append("AND ra.status = 'ACTIVE' AND " + SqlFragments.EFFECTIVE_END + " < ? " + notRenewed());
                params.add(today);
            }
            case RENEWED -> where.append(renewalSupported()
                    ? "AND EXISTS (SELECT 1 FROM rental_agreement r2 WHERE r2.renewed_from_agreement_id = ra.agreement_id) "
                    : "AND 1 = 0 ");
            case TERMINATED -> {
                where.append("AND ra.status = 'TERMINATED' AND " + SqlFragments.EFFECTIVE_END + " < ? ");
                params.add(today);
            }
            default -> { }
        }
        params.add(limit);
        return queryList("search agreement rows", ROW_SELECT + where + "ORDER BY ra.start_date DESC, ra.agreement_id DESC LIMIT ?",
                ps -> {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                }, ROW_MAPPER);
    }
}
