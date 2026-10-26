package com.estateflow.service.biometric;

public record EncryptedFaceTemplate(int keyVersion, int templateVersion, String modelVersion, byte[] nonce, byte[] ciphertext) {
    public EncryptedFaceTemplate {
        if (keyVersion < 1 || templateVersion < 1 || modelVersion == null || modelVersion.isBlank() || nonce == null || nonce.length != 12 || ciphertext == null || ciphertext.length < 16 || ciphertext.length > 8192)
            throw new IllegalArgumentException("Invalid encrypted face template metadata.");
        nonce = nonce.clone(); ciphertext = ciphertext.clone();
    }
    @Override public byte[] nonce() { return nonce.clone(); }
    @Override public byte[] ciphertext() { return ciphertext.clone(); }
    @Override public String toString() { return "EncryptedFaceTemplate[redacted]"; }
}
