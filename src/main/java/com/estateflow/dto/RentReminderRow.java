package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One unpaid, live rent charge that is overdue, due today, or due within the reminder window. */
public record RentReminderRow(
        long chargeId, long agreementId, String propertyName, String tenantName, String tenantPhone,
        LocalDate periodStart, LocalDate dueDate, BigDecimal balance, long daysUntilDue) {

    public enum Category {OVERDUE, DUE_TODAY, UPCOMING}

    public Category category() {
        return daysUntilDue < 0 ? Category.OVERDUE : daysUntilDue == 0 ? Category.DUE_TODAY : Category.UPCOMING;
    }
}
