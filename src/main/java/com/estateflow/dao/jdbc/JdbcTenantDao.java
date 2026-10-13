package com.estateflow.dao.jdbc;

import com.estateflow.dao.TenantDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.Tenant;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcTenantDao extends BaseJdbcDao implements TenantDao {

    private static final String COLUMNS = "tenant_id, full_name, phone, email, address, id_proof_type, "
            + "id_proof_number, emergency_contact_name, emergency_contact_phone, is_active, created_at";

    private static final RowMapper<Tenant> MAPPER = rs -> {
        Tenant t = new Tenant();
        t.setId(rs.getLong("tenant_id"));
        t.setFullName(rs.getString("full_name"));
        t.setPhone(rs.getString("phone"));
        t.setEmail(rs.getString("email"));
        t.setAddress(rs.getString("address"));
        t.setIdProofType(rs.getString("id_proof_type"));
        t.setIdProofNumber(rs.getString("id_proof_number"));
        t.setEmergencyContactName(rs.getString("emergency_contact_name"));
        t.setEmergencyContactPhone(rs.getString("emergency_contact_phone"));
        t.setActive(rs.getBoolean("is_active"));
        t.setCreatedAt(dateTime(rs, "created_at"));
        return t;
    };

    public JdbcTenantDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    private static void bindFields(PreparedStatement ps, Tenant t) throws SQLException {
        ps.setString(1, t.getFullName());
        ps.setString(2, t.getPhone());
        ps.setString(3, t.getEmail());
        ps.setString(4, t.getAddress());
        ps.setString(5, t.getIdProofType());
        ps.setString(6, t.getIdProofNumber());
        ps.setString(7, t.getEmergencyContactName());
        ps.setString(8, t.getEmergencyContactPhone());
        ps.setBoolean(9, t.isActive());
    }

    @Override
    public Tenant insert(Tenant t) {
        long id = insertReturningKey("insert tenant", "INSERT INTO tenant (full_name, phone, email, address, "
                + "id_proof_type, id_proof_number, emergency_contact_name, emergency_contact_phone, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", ps -> bindFields(ps, t));
        t.setId(id);
        return t;
    }

    @Override
    public int update(Tenant t) {
        return executeUpdate("update tenant", "UPDATE tenant SET full_name = ?, phone = ?, email = ?, address = ?, "
                + "id_proof_type = ?, id_proof_number = ?, emergency_contact_name = ?, emergency_contact_phone = ?, "
                + "is_active = ? WHERE tenant_id = ?", ps -> {
            bindFields(ps, t);
            ps.setLong(10, t.getId());
        });
    }

    @Override
    public Optional<Tenant> findById(Long id) {
        return queryOne("find tenant by id", "SELECT " + COLUMNS + " FROM tenant WHERE tenant_id = ?",
                ps -> ps.setLong(1, id), MAPPER);
    }

    @Override
    public List<Tenant> findAll() {
        return queryList("list tenants", "SELECT " + COLUMNS + " FROM tenant ORDER BY full_name", Binder.NONE, MAPPER);
    }

    @Override
    public Optional<Tenant> findByPhone(String phone) {
        return queryOne("find tenant by phone", "SELECT " + COLUMNS + " FROM tenant WHERE phone = ?",
                ps -> ps.setString(1, phone), MAPPER);
    }

    @Override
    public Optional<Tenant> findByIdProof(String type, String number) {
        return queryOne("find tenant by id proof", "SELECT " + COLUMNS
                + " FROM tenant WHERE id_proof_type = ? AND id_proof_number = ?", ps -> {
            ps.setString(1, type);
            ps.setString(2, number);
        }, MAPPER);
    }

    @Override
    public List<Tenant> search(String text) {
        return queryList("search tenants", "SELECT " + COLUMNS + " FROM tenant WHERE full_name LIKE ? OR phone LIKE ? "
                + "OR email LIKE ? OR id_proof_number LIKE ? ORDER BY full_name", ps -> {
            for (int i = 1; i <= 4; i++) {
                ps.setString(i, like(text));
            }
        }, MAPPER);
    }

    @Override
    public List<Tenant> search(String text, int limit) {
        return queryList("search tenants (capped)", "SELECT " + COLUMNS + " FROM tenant WHERE full_name LIKE ? OR phone LIKE ? "
                + "OR email LIKE ? OR id_proof_number LIKE ? ORDER BY full_name LIMIT ?", ps -> {
            for (int i = 1; i <= 4; i++) {
                ps.setString(i, like(text));
            }
            ps.setInt(5, limit);
        }, MAPPER);
    }
}
