package com.estateflow.service.biometric;

public final class CameraUnavailableException extends Exception {
    public CameraUnavailableException(String message) { super(message); }
    public CameraUnavailableException(String message, Throwable cause) { super(message, cause); }
}
