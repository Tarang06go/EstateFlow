package com.estateflow.util;

import com.estateflow.config.UserPreferences;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Presentation formatting for the JavaFX screens, driven by the user's preferences. */
public final class UiFormat {

    private final UserPreferences preferences;

    public UiFormat(UserPreferences preferences) {
        this.preferences = preferences;
    }

    /** "₹ 1,234.50" (symbol per preference); null-safe. */
    public String money(BigDecimal amount) {
        String symbol = preferences.currencySymbol();
        String number = MoneyUtil.format(amount);
        return symbol.isEmpty() ? number : symbol + " " + number;
    }

    public String date(LocalDate date) {
        return date == null ? "" : DateTimeFormatter.ofPattern(preferences.datePattern(), Locale.ENGLISH).format(date);
    }

    public UserPreferences preferences() {
        return preferences;
    }
}
