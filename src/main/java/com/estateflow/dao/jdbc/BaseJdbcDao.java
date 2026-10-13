package com.estateflow.dao.jdbc;

import com.estateflow.db.ConnectionHandle;
import com.estateflow.db.DatabaseManager;
import com.estateflow.exception.DataAccessException;
import com.estateflow.exception.DuplicateKeyException;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared JDBC plumbing: connection acquisition through {@link DatabaseManager#acquire()}, query helpers
 * and the single place where {@link SQLException} is translated. DAOs never log, commit, roll back,
 * or close a raw Connection.
 */
public abstract class BaseJdbcDao {

    @FunctionalInterface
    protected interface Binder {
        Binder NONE = ps -> {
        };

        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    protected interface ConnectionWork<T> {
        T run(Connection connection) throws SQLException;
    }

    protected final DatabaseManager databaseManager;

    protected BaseJdbcDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    protected <T> T withConnection(String operation, ConnectionWork<T> work) {
        try (ConnectionHandle handle = databaseManager.acquire()) {
            return work.run(handle.connection());
        } catch (SQLException e) {
            throw translate(e, operation);
        }
    }

    protected <T> List<T> queryList(String operation, String sql, Binder binder, RowMapper<T> mapper) {
        return withConnection(operation, c -> list(c, sql, binder, mapper));
    }

    protected <T> Optional<T> queryOne(String operation, String sql, Binder binder, RowMapper<T> mapper) {
        return withConnection(operation, c -> one(c, sql, binder, mapper));
    }

    protected int executeUpdate(String operation, String sql, Binder binder) {
        return withConnection(operation, c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                binder.bind(ps);
                return ps.executeUpdate();
            }
        });
    }

    protected long insertReturningKey(String operation, String sql, Binder binder) {
        return withConnection(operation, c -> {
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                binder.bind(ps);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No generated key returned", "HY000");
                    }
                    return keys.getLong(1);
                }
            }
        });
    }

    protected boolean exists(String operation, String sql, Binder binder) {
        return queryOne(operation, sql, binder, rs -> 1).isPresent();
    }

    protected static <T> List<T> list(Connection c, String sql, Binder binder, RowMapper<T> mapper) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        }
    }

    protected static <T> Optional<T> one(Connection c, String sql, Binder binder, RowMapper<T> mapper) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    /** The only place SQL error codes are inspected. Keeps the SQLException as the cause. */
    protected DataAccessException translate(SQLException e, String operation) {
        String message = operation + " failed [SQLState=" + e.getSQLState() + ", code=" + e.getErrorCode() + "]";
        if (e.getErrorCode() == 1062) {
            return new DuplicateKeyException(message, e, constraintName(e.getMessage()));
        }
        return new DataAccessException(message, e);
    }

    /** Best effort: "... for key 'tenant.uq_tenant_phone'" gives "uq_tenant_phone". */
    static String constraintName(String message) {
        if (message == null) {
            return null;
        }
        int marker = message.lastIndexOf("for key '");
        if (marker < 0) {
            return null;
        }
        int start = marker + "for key '".length();
        int end = message.indexOf('\'', start);
        if (end < 0) {
            return null;
        }
        String key = message.substring(start, end);
        return key.substring(key.lastIndexOf('.') + 1);
    }

    // ---- value helpers shared by the DAOs

    protected static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    protected static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    protected static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    protected static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    protected static String like(String text) {
        return "%" + text.trim() + "%";
    }
}
