package com.estateflow.service.rules;

import com.estateflow.exception.ValidationException;
import com.estateflow.model.Person;
import com.estateflow.model.Tenant;
import com.estateflow.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Reusable validation rules for domain entities and inputs.
 * Throws ValidationException on invalid input.
 */
public final class Validators {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_DIGITS_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");

    private Validators() {}

    /**
     * Normalizes a phone number (BR-01):
     * Trims surrounding whitespace and removes formatting spaces, hyphens, and parentheses.
     * Preserves a leading + if supplied.
     */
    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";

        boolean hasPlus = trimmed.startsWith("+");
        // remove all non-digits
        String digits = trimmed.replaceAll("[^0-9]", "");
        return hasPlus ? "+" + digits : digits;
    }

    public static void requireNonBlank(String value, String fieldName, String message) throws ValidationException {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(message != null ? message : fieldName + " is required", fieldName);
        }
    }

    public static void validatePhone(String phone, String fieldName) throws ValidationException {
        requireNonBlank(phone, fieldName, "Phone number is required");
        String normalized = normalizePhone(phone);
        if (!PHONE_DIGITS_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Phone number must contain 7 to 15 digits", fieldName);
        }
    }

    public static void validateEmail(String email, String fieldName) throws ValidationException {
        if (email != null && !email.trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
                throw new ValidationException("Invalid email address format", fieldName);
            }
        }
    }

    /**
     * Validates shared contact fields for Person (Owner or Tenant).
     */
    public static void validateContact(Person person) throws ValidationException {
        if (person == null) {
            throw new ValidationException("Person cannot be null", null);
        }
        requireNonBlank(person.getFullName(), "fullName", "Full name is required");
        validatePhone(person.getPhone(), "phone");
        validateEmail(person.getEmail(), "email");

        if (person instanceof Tenant tenant) {
            validateTenantIdProof(tenant.getIdProofType(), tenant.getIdProofNumber());
            if (tenant.getEmergencyContactPhone() != null && !tenant.getEmergencyContactPhone().isBlank()) {
                validatePhone(tenant.getEmergencyContactPhone(), "emergencyContactPhone");
            }
        }
    }

    /**
     * BR-01: Tenant ID proof type and number must either both be absent or both be present.
     */
    public static void validateTenantIdProof(String type, String number) throws ValidationException {
        boolean hasType = type != null && !type.trim().isEmpty();
        boolean hasNumber = number != null && !number.trim().isEmpty();
        if (hasType != hasNumber) {
            throw new ValidationException(
                    "ID proof type and number must either both be provided or both be empty",
                    hasType ? "idProofNumber" : "idProofType"
            );
        }
    }

    public static void validatePositiveMoney(BigDecimal amount, String fieldName, String message) throws ValidationException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(message != null ? message : fieldName + " must be greater than zero", fieldName);
        }
        if (!MoneyUtil.hasValidScale(amount)) {
            throw new ValidationException(fieldName + " cannot have more than 2 decimal places", fieldName);
        }
    }

    public static void validateNonNegativeMoney(BigDecimal amount, String fieldName, String message) throws ValidationException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(message != null ? message : fieldName + " cannot be negative", fieldName);
        }
        if (!MoneyUtil.hasValidScale(amount)) {
            throw new ValidationException(fieldName + " cannot have more than 2 decimal places", fieldName);
        }
    }

    public static void validateDateOrder(LocalDate start, LocalDate end, String fieldName, String message) throws ValidationException {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new ValidationException(message != null ? message : "End date must be after start date", fieldName);
        }
    }
}
