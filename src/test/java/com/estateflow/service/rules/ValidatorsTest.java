package com.estateflow.service.rules;

import com.estateflow.exception.ValidationException;
import com.estateflow.model.Owner;
import com.estateflow.model.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorsTest {

    @Test
    @DisplayName("BR-01: normalizePhone removes formatting and preserves leading +")
    void testNormalizePhone() {
        assertEquals("+919876543210", Validators.normalizePhone("+91 (987) 654-3210"));
        assertEquals("9876543210", Validators.normalizePhone("  987-654 3210  "));
        assertEquals("", Validators.normalizePhone("   "));
        assertNull(Validators.normalizePhone(null));
    }

    @Test
    @DisplayName("validatePhone accepts valid numbers and rejects invalid")
    void testValidatePhone() {
        assertDoesNotThrow(() -> Validators.validatePhone("+919876543210", "phone"));
        assertDoesNotThrow(() -> Validators.validatePhone("9876543210", "phone"));

        assertThrows(ValidationException.class, () -> Validators.validatePhone("123", "phone"));
        assertThrows(ValidationException.class, () -> Validators.validatePhone("abc", "phone"));
        assertThrows(ValidationException.class, () -> Validators.validatePhone("", "phone"));
    }

    @Test
    @DisplayName("validateEmail accepts valid format and null/empty (optional)")
    void testValidateEmail() {
        assertDoesNotThrow(() -> Validators.validateEmail("john@example.com", "email"));
        assertDoesNotThrow(() -> Validators.validateEmail(null, "email"));
        assertDoesNotThrow(() -> Validators.validateEmail("", "email"));

        assertThrows(ValidationException.class, () -> Validators.validateEmail("not-an-email", "email"));
        assertThrows(ValidationException.class, () -> Validators.validateEmail("missing@domain", "email"));
    }

    @Test
    @DisplayName("BR-01: validateTenantIdProof enforces pair invariant")
    void testValidateTenantIdProof() {
        // Both null or both non-empty -> valid
        assertDoesNotThrow(() -> Validators.validateTenantIdProof(null, null));
        assertDoesNotThrow(() -> Validators.validateTenantIdProof("  ", "  "));
        assertDoesNotThrow(() -> Validators.validateTenantIdProof("AADHAAR", "1234-5678-9012"));

        // One present, other missing -> invalid
        assertThrows(ValidationException.class, () -> Validators.validateTenantIdProof("PASSPORT", null));
        assertThrows(ValidationException.class, () -> Validators.validateTenantIdProof(null, "A1234567"));
        assertThrows(ValidationException.class, () -> Validators.validateTenantIdProof("PASSPORT", ""));
    }

    @Test
    @DisplayName("validateContact tests owner and tenant contact validation")
    void testValidateContact() {
        Owner owner = new Owner("John Doe", "+1234567890", "john@test.com", "123 Main St");
        assertDoesNotThrow(() -> Validators.validateContact(owner));

        Tenant tenant = new Tenant("Jane Smith", "+1987654321", null, null);
        tenant.setIdProofType("PAN");
        tenant.setIdProofNumber("ABCDE1234F");
        assertDoesNotThrow(() -> Validators.validateContact(tenant));

        // Invalid emergency phone
        tenant.setEmergencyContactPhone("invalid");
        assertThrows(ValidationException.class, () -> Validators.validateContact(tenant));
    }

    @Test
    @DisplayName("validatePositiveMoney and validateNonNegativeMoney")
    void testMoneyValidation() {
        assertDoesNotThrow(() -> Validators.validatePositiveMoney(new BigDecimal("100.00"), "rent", null));
        assertThrows(ValidationException.class, () -> Validators.validatePositiveMoney(BigDecimal.ZERO, "rent", null));
        assertThrows(ValidationException.class, () -> Validators.validatePositiveMoney(new BigDecimal("-10.00"), "rent", null));
        assertThrows(ValidationException.class, () -> Validators.validatePositiveMoney(new BigDecimal("100.555"), "rent", null));

        assertDoesNotThrow(() -> Validators.validateNonNegativeMoney(BigDecimal.ZERO, "deposit", null));
        assertThrows(ValidationException.class, () -> Validators.validateNonNegativeMoney(new BigDecimal("-5.00"), "deposit", null));
    }

    @Test
    @DisplayName("validateDateOrder requires end after start")
    void testDateOrder() {
        assertDoesNotThrow(() -> Validators.validateDateOrder(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "end", null));
        assertThrows(ValidationException.class, () -> Validators.validateDateOrder(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), "end", null));
        assertThrows(ValidationException.class, () -> Validators.validateDateOrder(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), "end", null));
    }
}
