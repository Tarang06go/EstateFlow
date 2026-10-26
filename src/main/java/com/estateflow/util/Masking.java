package com.estateflow.util;

/** Hides most of sensitive identifiers when they are shown in lists and profiles. */
public final class Masking {

    private Masking() {
    }

    /** "2345-6789-0123" becomes "••••0123"; values of four characters or fewer are fully masked. */
    public static String idNumber(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String v = value.trim();
        String tail = v.length() > 4 ? v.substring(v.length() - 4) : "";
        return "\u2022\u2022\u2022\u2022" + tail;
    }
}
