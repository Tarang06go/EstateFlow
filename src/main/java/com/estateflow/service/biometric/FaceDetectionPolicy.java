package com.estateflow.service.biometric;

/** Exact-one-face decision kept separate so count behavior is testable without camera data. */
public final class FaceDetectionPolicy {
    private FaceDetectionPolicy() { }

    public static void requireExactlyOne(int detectionCount) throws FaceProcessingException {
        if (detectionCount == 0) {
            throw new FaceProcessingException(FaceProcessingException.Reason.NO_FACE,
                    "No face detected. Face the camera, move closer, and improve lighting.");
        }
        if (detectionCount > 1) {
            throw new FaceProcessingException(FaceProcessingException.Reason.MULTIPLE_FACES,
                    "More than one face detected. Ensure only one person is in the frame.");
        }
        if (detectionCount < 0) {
            throw new IllegalArgumentException("Detection count cannot be negative.");
        }
    }
}
