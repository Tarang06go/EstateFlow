package com.estateflow.exception;

/** Sign-in failed. The message is intentionally generic: it never says whether the username exists. */
public class AuthenticationException extends EstateFlowException {
    public AuthenticationException(String message) {
        super(message);
    }
}
