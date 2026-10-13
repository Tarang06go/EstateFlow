package com.estateflow.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility for monetary values in EstateFlow.
 * All monetary amounts are BigDecimal with scale 2 and RoundingMode.HALF_UP.
 */
public final class MoneyUtil {

    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
    public static final int DEFAULT_SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);

    private static final DecimalFormat FORMATTER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        FORMATTER = new DecimalFormat("#,##0.00", symbols);
    }

    private MoneyUtil() {}

    /**
     * Standardizes a BigDecimal to scale 2 with HALF_UP rounding.
     */
    public static BigDecimal round(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Formats an amount as a 2-decimal string (e.g. 1,234.50).
     */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return FORMATTER.format(ZERO);
        }
        return FORMATTER.format(round(amount));
    }

    /**
     * Checks if amount has at most 2 decimal places.
     */
    public static boolean hasValidScale(BigDecimal amount) {
        if (amount == null) return false;
        return amount.scale() <= DEFAULT_SCALE;
    }

    /**
     * Parses a string representation of money.
     * Removes commas and whitespace.
     */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String clean = text.trim().replace(",", "").replace("$", "").replace("₹", "");
        BigDecimal val = new BigDecimal(clean);
        return round(val);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNonNegative(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) >= 0;
    }
}
