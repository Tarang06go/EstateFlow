package com.estateflow.dao.jdbc;

import com.estateflow.dao.OwnerDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.Owner;

import java.util.List;
import java.util.Optional;

public class JdbcOwnerDao extends BaseJdbcDao implements OwnerDao {

    private static final String COLUMNS = "owner_id, full_name, phone, email, address, is_active, created_at";

    private static final RowMapper<Owner> MAPPER = rs -> {
        Owner o = new Owner();
        o.setId(rs.getLong("owner_id"));
        o.setFullName(rs.getString("full_name"));
        o.setPhone(rs.getString("phone"));
        o.setEmail(rs.getString("email"));
        o.setAddress(rs.getString("address"));
        o.setActive(rs.getBoolean("is_active"));
        o.setCreatedAt(dateTime(rs, "created_at"));
        return o;
    };

    public JdbcOwnerDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public Owner insert(Owner o) {
        long id = insertReturningKey("insert owner",
                "INSERT INTO owner (full_name, phone, email, address, is_active) VALUES (?, ?, ?, ?, ?)", ps -> {
                    ps.setString(1, o.getFullName());
                    ps.setString(2, o.getPhone());
                    ps.setString(3, o.getEmail());
                    ps.setString(4, o.getAddress());
                    ps.setBoolean(5, o.isActive());
                });
        o.setId(id);
        return o;
    }

    @Override
    public int update(Owner o) {
        return executeUpdate("update owner",
                "UPDATE owner SET full_name = ?, phone = ?, email = ?, address = ?, is_active = ? WHERE owner_id = ?", ps -> {
                    ps.setString(1, o.getFullName());
                    ps.setString(2, o.getPhone());
                    ps.setString(3, o.getEmail());
                    ps.setString(4, o.getAddress());
                    ps.setBoolean(5, o.isActive());
                    ps.setLong(6, o.getId());
                });
    }

    @Override
    public Optional<Owner> findById(Long id) {
        return queryOne("find owner by id", "SELECT " + COLUMNS + " FROM owner WHERE owner_id = ?",
                ps -> ps.setLong(1, id), MAPPER);
    }

    @Override
    public List<Owner> findAll() {
        return queryList("list owners", "SELECT " + COLUMNS + " FROM owner ORDER BY full_name", Binder.NONE, MAPPER);
    }

    @Override
    public Optional<Owner> findByPhone(String phone) {
        return queryOne("find owner by phone", "SELECT " + COLUMNS + " FROM owner WHERE phone = ?",
                ps -> ps.setString(1, phone), MAPPER);
    }

    @Override
    public List<Owner> search(String text) {
        return queryList("search owners", "SELECT " + COLUMNS + " FROM owner WHERE full_name LIKE ? OR phone LIKE ? "
                + "OR email LIKE ? OR address LIKE ? ORDER BY full_name", ps -> {
            for (int i = 1; i <= 4; i++) {
                ps.setString(i, like(text));
            }
        }, MAPPER);
    }

    @Override
    public List<Owner> search(String text, int limit) {
        return queryList("search owners (capped)", "SELECT " + COLUMNS + " FROM owner WHERE full_name LIKE ? OR phone LIKE ? "
                + "OR email LIKE ? OR address LIKE ? ORDER BY full_name LIMIT ?", ps -> {
            for (int i = 1; i <= 4; i++) {
                ps.setString(i, like(text));
            }
            ps.setInt(5, limit);
        }, MAPPER);
    }
}
