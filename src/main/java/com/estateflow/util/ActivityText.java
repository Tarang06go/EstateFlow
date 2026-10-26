package com.estateflow.util;

import com.estateflow.dto.AuditEntry;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns audit entries into short human-readable activity lines. Pure functions, no UI types. */
public final class ActivityText {

    private ActivityText() {
    }

    /** e.g. "Payment #12 recorded". */
    public static String describe(AuditEntry e) {
        String type = switch (e.entityType()) {
            case "PAYMENT" -> "Payment";
            case "AGREEMENT" -> "Agreement";
            case "OWNER" -> "Owner";
            case "TENANT" -> "Tenant";
            case "PROPERTY" -> "Property";
            case "MAINTENANCE" -> "Maintenance request";
            case "USER" -> "User account";
            case "EXPENSE" -> "Expense";
            case "DEPOSIT" -> "Security deposit";
            default -> capitalize(e.entityType());
        };
        String verb = switch (e.action()) {
            case "CREATED" -> "created";
            case "UPDATED" -> "updated";
            case "RECORDED" -> "recorded";
            case "VOIDED" -> "voided";
            case "TERMINATED" -> "terminated";
            case "DEACTIVATED" -> "deactivated";
            case "REACTIVATED" -> "reactivated";
            case "STATUS_CHANGED" -> "status changed";
            case "NOTES_UPDATED" -> "notes updated";
            case "DELETED" -> "deleted";
            case "COLLECTED" -> "collected";
            case "DEDUCTED" -> "deduction recorded";
            case "REFUNDED" -> "refunded";
            case "TXN_VOIDED" -> "entry voided";
            case "TRANSFERRED_IN" -> "carried in from the previous lease";
            case "TRANSFERRED_OUT" -> "carried to the renewed lease";
            case "EXTENDED" -> "extended";
            case "RENEWED" -> "renewed";
            case "ROLE_CHANGED" -> "role changed";
            case "PASSWORD_RESET" -> "password reset";
            case "SIGNED_IN" -> "signed in";
            case "SIGNED_OUT" -> "signed out";
            case "SIGN_IN_FAILED" -> "sign-in failed";
            default -> e.action().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
        String by = e.actorUsername() == null || e.action().startsWith("SIGN") ? "" : " by " + e.actorUsername();
        return type + " #" + e.entityId() + " " + verb + by;
    }

    /** Relative time for recent entries ("5 min ago"), a date for older ones. Audit timestamps are UTC. */
    public static String when(AuditEntry e, Clock clock) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Duration age = Duration.between(e.createdAt(), now);
        if (age.isNegative() || age.toMinutes() < 1) {
            return "Just now";
        }
        if (age.toMinutes() < 60) {
            return age.toMinutes() + " min ago";
        }
        if (age.toHours() < 24) {
            return age.toHours() + " h ago";
        }
        if (age.toHours() < 48) {
            return "Yesterday";
        }
        return DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH).format(e.createdAt());
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
