package com.estateflow.exception;

import java.sql.SQLException;

/**
 * Unique-constraint violation (MySQL error 1062).
 * Best-effort constraint name extraction.
 */
public class DuplicateKeyException extends DataAccessException {

    private final String constraintName;

    public DuplicateKeyException(String message, SQLException cause, String constraintName) {
        super(message, cause);
        this.constraintName = constraintName;
    }

    public String getConstraintName() {
        return constraintName;
    }
}
