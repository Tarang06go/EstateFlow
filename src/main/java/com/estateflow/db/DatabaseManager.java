package com.estateflow.db;

import com.estateflow.config.AppConfig;
import com.estateflow.exception.DataAccessException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates physical connections and hands them out as {@link ConnectionHandle}s. It also holds the
 * thread-bound transaction connection, which only {@link JdbcTransactionManager} binds. It never
 * commits or rolls back.
 */
public class DatabaseManager {

    private final String url;
    private final String username;
    private final String password;
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public DatabaseManager(AppConfig config) {
        this(config.dbUrl(), config.dbUsername(), config.dbPassword());
    }

    public DatabaseManager(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /** Borrowed handle inside a transaction on this thread, otherwise a handle owning a fresh connection. */
    public ConnectionHandle acquire() {
        Connection bound = transactionConnection.get();
        if (bound != null) {
            return ConnectionHandle.borrowed(bound);
        }
        return ConnectionHandle.owned(openConnection());
    }

    /** The JDBC URL without credentials, useful for diagnostics and the _test safety check. */
    public String url() {
        return url;
    }

    Connection openConnection() {
        try {
            Connection connection = DriverManager.getConnection(url, username, password);
            try (Statement st = connection.createStatement()) {
                st.execute("SET time_zone = '+00:00'");
            } catch (SQLException e) {
                connection.close();
                throw e;
            }
            return connection;
        } catch (SQLException e) {
            throw new DataAccessException("open database connection failed [SQLState=" + e.getSQLState()
                    + ", code=" + e.getErrorCode() + "]", e);
        }
    }

    boolean isTransactionBound() {
        return transactionConnection.get() != null;
    }

    void bind(Connection connection) {
        transactionConnection.set(connection);
    }

    void unbind() {
        transactionConnection.remove();
    }
}
