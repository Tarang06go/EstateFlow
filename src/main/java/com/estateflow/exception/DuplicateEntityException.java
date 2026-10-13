package com.estateflow.exception;

/**
 * A unique value already exists in the database.
 */
public class DuplicateEntityException extends EstateFlowException {

    public DuplicateEntityException(String message) {
        super(message);
    }

    public DuplicateEntityException(String message, Throwable cause) {
        super(message, cause);
    }
}
