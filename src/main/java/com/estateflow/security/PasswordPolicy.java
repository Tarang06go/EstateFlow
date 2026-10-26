package com.estateflow.security;

import com.estateflow.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Username and password rules for accounts. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");

    private PasswordPolicy() {
    }

    /** Lower-cases and trims; throws when the result is not 3-50 characters of letters, digits, dot, dash, underscore. */
    public static String normalizeUsername(String raw) throws ValidationException {
        String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(name).matches()) {
            throw new ValidationException("Username must be 3-50 characters: letters, digits, dot, dash or underscore.");
        }
        return name;
    }

    public static void validatePassword(String password, String username) throws ValidationException {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new ValidationException("Password must be at most " + MAX_LENGTH + " characters.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new ValidationException("Password must contain at least one letter and one digit.");
        }
        if (username != null && !username.isBlank()
                && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("Password must not contain the username.");
        }
    }
}
