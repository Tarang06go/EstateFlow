package com.estateflow.service.biometric;

public final class FaceProcessingException extends Exception {
    public enum Reason { MODEL, INVALID_FRAME, LIGHTING, NO_FACE, MULTIPLE_FACES, FACE_SIZE, DETECTION, ALIGNMENT, EMBEDDING }

    private final Reason reason;

    public FaceProcessingException(String message) { this(Reason.DETECTION, message, null); }
    public FaceProcessingException(String message, Throwable cause) { this(Reason.DETECTION, message, cause); }
    public FaceProcessingException(Reason reason, String message) { this(reason, message, null); }
    public FaceProcessingException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
