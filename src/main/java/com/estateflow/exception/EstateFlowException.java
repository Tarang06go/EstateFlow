package com.estateflow.exception;

/**
 * Base checked exception for EstateFlow business failures.
 * Message is always safe to show the user.
 */
public class EstateFlowException extends Exception {

    public EstateFlowException(String message) {
        super(message);
    }

    public EstateFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
