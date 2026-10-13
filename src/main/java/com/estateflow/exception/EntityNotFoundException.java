package com.estateflow.exception;

/**
 * The requested entity was not found (e.g. record changed by someone else).
 */
public class EntityNotFoundException extends EstateFlowException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
