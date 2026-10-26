package com.estateflow.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(2_000);

    @Test
    void hashIsNotPlaintextAndHasExpectedFormat() {
        String hash = hasher.hash("Correct-Horse-9");
        assertFalse(hash.contains("Correct-Horse-9"));
        assertTrue(hash.startsWith("pbkdf2_sha256$2000$"));
        assertEquals(4, hash.split("\\$").length);
    }

    @Test
    void correctPasswordVerifiesWrongOneDoesNot() {
        String hash = hasher.hash("Correct-Horse-9");
        assertTrue(hasher.verify("Correct-Horse-9", hash));
        assertFalse(hasher.verify("correct-horse-9", hash));
        assertFalse(hasher.verify("", hash));
    }

    @Test
    void samePasswordGetsDifferentSalts() {
        assertNotEquals(hasher.hash("Same-Password-1"), hasher.hash("Same-Password-1"));
    }

    @Test
    void malformedOrNullStoredValuesNeverVerify() {
        assertFalse(hasher.verify("x", null));
        assertFalse(hasher.verify(null, hasher.hash("abc12345678")));
        assertFalse(hasher.verify("x", "plaintext"));
        assertFalse(hasher.verify("x", "pbkdf2_sha256$notanumber$AAAA$BBBB"));
        assertFalse(hasher.verify("x", "pbkdf2_sha256$5$AAAA$BBBB"));
        assertFalse(hasher.verify("x", "other$2000$AAAA$BBBB"));
        assertFalse(hasher.verify("x", "pbkdf2_sha256$2000000000$AAAA$BBBB"), "absurd iteration count must not hang");
    }

    @Test
    void hashCreatedWithOtherIterationCountStillVerifies() {
        String hash = new PasswordHasher(1_500).hash("Pass-Word-123");
        assertTrue(hasher.verify("Pass-Word-123", hash));
    }

    @Test
    void tooFewIterationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(10));
    }
}
