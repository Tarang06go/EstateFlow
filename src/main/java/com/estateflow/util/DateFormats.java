package com.estateflow.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Presentation formatting for dates; services and DAOs never format for the UI. */
public final class DateFormats {
    public static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private DateFormats() {
    }

    public static String format(LocalDate date) {
        return date == null ? "" : DISPLAY.format(date);
    }
}
