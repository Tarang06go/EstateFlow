package com.estateflow.model;

import java.time.LocalDateTime;

/** Encrypted biometric persistence record; never contains an image or plaintext feature. */
public record FaceEnrollmentRecord(long userId, String deviceId, byte[] ciphertext, byte[] nonce,
                                   int keyVersion, int templateVersion, String modelVersion,
                                   String consentVersion, LocalDateTime consentedAt, boolean enabled) {
    public FaceEnrollmentRecord {
        ciphertext = ciphertext.clone(); nonce = nonce.clone();
    }
    @Override public byte[] ciphertext() { return ciphertext.clone(); }
    @Override public byte[] nonce() { return nonce.clone(); }
    @Override public String toString() { return "FaceEnrollmentRecord[redacted]"; }
}
