package com.estateflow.dao.jdbc;

import com.estateflow.dao.UserDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class JdbcUserDao extends BaseJdbcDao implements UserDao {

    private static final String COLUMNS = "user_id, username, role, is_active, created_at, last_login_at";

    public JdbcUserDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    private static AppUser map(ResultSet rs) throws SQLException {
        return new AppUser(rs.getLong("user_id"), rs.getString("username"), Role.valueOf(rs.getString("role")),
                rs.getBoolean("is_active"), dateTime(rs, "created_at"), dateTime(rs, "last_login_at"));
    }

    @Override
    public boolean isAvailable() {
        return exists("check app_user table", "SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'app_user' LIMIT 1", Binder.NONE);
    }

    @Override
    public Optional<Credentials> findCredentialsByUsername(String username) {
        return queryOne("find account by username", "SELECT " + COLUMNS + ", password_hash FROM app_user WHERE username = ?",
                ps -> ps.setString(1, username), rs -> new Credentials(map(rs), rs.getString("password_hash")));
    }

    @Override
    public Optional<AppUser> findUserByUsername(String username) {
        return queryOne("find account by username without credentials", "SELECT " + COLUMNS + " FROM app_user WHERE username = ?",
                ps -> ps.setString(1, username), JdbcUserDao::map);
    }

    @Override
    public Optional<AppUser> findById(long userId) {
        return queryOne("find account", "SELECT " + COLUMNS + " FROM app_user WHERE user_id = ?",
                ps -> ps.setLong(1, userId), JdbcUserDao::map);
    }

    @Override
    public List<AppUser> findAll() {
        return queryList("list accounts", "SELECT " + COLUMNS + " FROM app_user ORDER BY username", Binder.NONE,
                JdbcUserDao::map);
    }

    @Override
    public AppUser insert(String username, String passwordHash, Role role) {
        long id = insertReturningKey("insert account",
                "INSERT INTO app_user (username, password_hash, role, is_active) VALUES (?, ?, ?, 1)", ps -> {
                    ps.setString(1, username);
                    ps.setString(2, passwordHash);
                    ps.setString(3, role.name());
                });
        return findById(id).orElseThrow(() -> new IllegalStateException("Inserted account not found"));
    }

    @Override
    public int updateRole(long userId, Role role) {
        return executeUpdate("change account role", "UPDATE app_user SET role = ? WHERE user_id = ?", ps -> {
            ps.setString(1, role.name());
            ps.setLong(2, userId);
        });
    }

    @Override
    public int updateActive(long userId, boolean active) {
        return executeUpdate("change account status", "UPDATE app_user SET is_active = ? WHERE user_id = ?", ps -> {
            ps.setBoolean(1, active);
            ps.setLong(2, userId);
        });
    }

    @Override
    public int updatePasswordHash(long userId, String passwordHash) {
        return executeUpdate("reset account password", "UPDATE app_user SET password_hash = ? WHERE user_id = ?", ps -> {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
        });
    }

    @Override
    public void touchLastLogin(long userId, LocalDateTime utc) {
        executeUpdate("record sign-in time", "UPDATE app_user SET last_login_at = ? WHERE user_id = ?", ps -> {
            ps.setObject(1, utc);
            ps.setLong(2, userId);
        });
    }

    @Override
    public long countActiveAdmins() {
        return queryOne("count active administrators",
                "SELECT COUNT(*) AS n FROM app_user WHERE role = 'ADMIN' AND is_active = 1", Binder.NONE,
                rs -> rs.getLong("n")).orElse(0L);
    }
}
