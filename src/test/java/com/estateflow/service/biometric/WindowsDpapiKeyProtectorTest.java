package com.estateflow.service.biometric;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class WindowsDpapiKeyProtectorTest {
    @Test void dpapiRoundTripUsesCurrentWindowsUserProfile() {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("windows"));
        byte[] key=new byte[32]; new java.security.SecureRandom().nextBytes(key);
        WindowsDpapiKeyProtector protector=new WindowsDpapiKeyProtector();
        byte[] wrapped=protector.protect(key);
        assertFalse(Arrays.equals(key,wrapped));
        assertArrayEquals(key,protector.unprotect(wrapped));
        Arrays.fill(key,(byte)0); Arrays.fill(wrapped,(byte)0);
    }
}
