package com.estateflow.security;

import com.estateflow.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void usernameIsTrimmedAndLowerCased() throws Exception {
        assertEquals("jane.doe", PasswordPolicy.normalizeUsername("  Jane.Doe "));
    }

    @Test
    void badUsernamesAreRejected() {
        for (String bad : new String[]{null, "", "ab", "has space", "semi;colon", "x".repeat(51)}) {
            assertThrows(ValidationException.class, () -> PasswordPolicy.normalizeUsername(bad), String.valueOf(bad));
        }
    }

    @Test
    void passwordRules() throws Exception {
        PasswordPolicy.validatePassword("goodpass123", "jane");
        assertThrows(ValidationException.class, () -> PasswordPolicy.validatePassword("short1", "jane"));
        assertThrows(ValidationException.class, () -> PasswordPolicy.validatePassword("onlyletterspassword", "jane"));
        assertThrows(ValidationException.class, () -> PasswordPolicy.validatePassword("1234567890123", "jane"));
        assertThrows(ValidationException.class, () -> PasswordPolicy.validatePassword("my-JANE-pass1", "jane"));
        assertThrows(ValidationException.class, () -> PasswordPolicy.validatePassword("a1".repeat(70), "jane"));
        assertThrows(ValidationException.class, () -> PasswordPolicy.validatePassword(null, "jane"));
    }
}
