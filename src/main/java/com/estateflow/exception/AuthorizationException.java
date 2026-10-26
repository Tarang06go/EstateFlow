package com.estateflow.exception;

/** The signed-in user (or nobody) may not perform an operation. Unchecked so it can guard read methods too. Safe to show. */
public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }
}
