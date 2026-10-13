package com.estateflow.util;

import com.estateflow.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Converts raw form input into typed values, reporting problems as ValidationException. */
public final class InputParser {

    private InputParser() {
    }

    public static String optionalText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public static String requiredText(String field, String label, String text) throws ValidationException {
        if (text == null || text.isBlank()) {
            throw new ValidationException(label + " is required.", field);
        }
        return text.trim();
    }

    public static BigDecimal requiredMoney(String field, String label, String text) throws ValidationException {
        if (text == null || text.isBlank()) {
            throw new ValidationException(label + " is required.", field);
        }
        return parseMoney(field, label, text);
    }

    public static BigDecimal optionalMoney(String field, String label, String text) throws ValidationException {
        return text == null || text.isBlank() ? null : parseMoney(field, label, text);
    }

    private static BigDecimal parseMoney(String field, String label, String text) throws ValidationException {
        try {
            return new BigDecimal(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new ValidationException(label + " must be a number such as 15000.00.", field, e);
        }
    }

    public static int requiredInt(String field, String label, String text) throws ValidationException {
        if (text == null || text.isBlank()) {
            throw new ValidationException(label + " is required.", field);
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(label + " must be a whole number.", field, e);
        }
    }

    public static Integer optionalInt(String field, String label, String text) throws ValidationException {
        return text == null || text.isBlank() ? null : requiredInt(field, label, text);
    }

    public static LocalDate requiredDate(String field, String label, LocalDate date) throws ValidationException {
        if (date == null) {
            throw new ValidationException(label + " is required.", field);
        }
        return date;
    }
}
