package com.estateflow.db;

import com.estateflow.exception.DataAccessException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * AutoCloseable wrapper that makes DAO code identical inside and outside a transaction.
 * A borrowed handle (transaction connection) ignores close(); an owned handle really closes.
 */
public final class ConnectionHandle implements AutoCloseable {

    private final Connection connection;
    private final boolean owned;

    private ConnectionHandle(Connection connection, boolean owned) {
        this.connection = connection;
        this.owned = owned;
    }

    static ConnectionHandle owned(Connection connection) {
        return new ConnectionHandle(connection, true);
    }

    static ConnectionHandle borrowed(Connection connection) {
        return new ConnectionHandle(connection, false);
    }

    public Connection connection() {
        return connection;
    }

    public boolean isOwned() {
        return owned;
    }

    @Override
    public void close() {
        if (!owned) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("close database connection failed [SQLState=" + e.getSQLState()
                    + ", code=" + e.getErrorCode() + "]", e);
        }
    }
}
