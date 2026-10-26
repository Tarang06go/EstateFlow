package com.estateflow.service;

import java.util.UUID;
import com.estateflow.service.biometric.FaceProcessingException;

/** Safe user-facing enrollment failure with a non-sensitive stage and reference for local diagnostics. */
public final class FaceEnrollmentException extends RuntimeException {
    public enum Stage {
        PROVIDER_CHECK,
        CAPTURE_AND_FACE_PROCESSING,
        SAMPLE_CONSISTENCY,
        PASSWORD_REAUTHENTICATION,
        LOCAL_KEY_AND_ENCRYPTION,
        DATABASE_PERSISTENCE
    }

    private final String diagnosticId = UUID.randomUUID().toString();
    private final Stage stage;
    private final String userMessage;

    private FaceEnrollmentException(Stage stage, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.stage = stage;
        this.userMessage = userMessage;
    }

    public static FaceEnrollmentException at(Stage stage, Throwable cause) {
        String message = stage == Stage.CAPTURE_AND_FACE_PROCESSING && cause instanceof FaceProcessingException processing
                ? processing.getMessage() + " Five usable samples are required; no enrollment was saved."
                : switch (stage) {
            case PROVIDER_CHECK -> "Face matching or camera support is unavailable. Password sign-in remains available.";
            case CAPTURE_AND_FACE_PROCESSING -> cause instanceof com.estateflow.service.biometric.CameraUnavailableException
                    ? "Camera access or frame capture failed. Check camera permissions and retry; no enrollment was saved."
                    : "Five usable face samples could not be captured. Check camera access, lighting, and that one face is visible, then retry.";
            case SAMPLE_CONSISTENCY -> "The face samples were inconsistent. Keep your face centered and steady in even lighting, then retry.";
            case PASSWORD_REAUTHENTICATION -> "Your password could not be reverified. No enrollment change was saved.";
            case LOCAL_KEY_AND_ENCRYPTION -> "Secure local key protection or template encryption failed. No enrollment change was saved.";
            case DATABASE_PERSISTENCE -> "The encrypted enrollment could not be saved. Check the v1.8 database migration; no enrollment change was committed.";
        };
        return new FaceEnrollmentException(stage, message, cause);
    }

    public Stage stage() { return stage; }
    public String diagnosticId() { return diagnosticId; }
    public String userMessage() { return userMessage; }
}
