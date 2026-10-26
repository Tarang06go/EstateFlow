package com.estateflow.service.biometric;

public interface KeyProtector {
    byte[] protect(byte[] key);
    byte[] unprotect(byte[] protectedKey);
}
