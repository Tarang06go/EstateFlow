package com.estateflow.exception;

import java.sql.SQLException;

/**
 * Unchecked exception for infrastructure/database failures.
 * Always carries the original SQLException as cause.
 * Message includes the operation name, SQLState, and vendor error code.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, SQLException cause) {
        super(message, cause);
    }

    @Override
    public synchronized SQLException getCause() {
        return (SQLException) super.getCause();
    }
}
