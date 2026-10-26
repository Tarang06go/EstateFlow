package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A maintenance request an expense can be linked to (no other active expense is linked to it). */
public record LinkableMaintenance(long requestId, String title, String status, BigDecimal cost, LocalDate resolvedDate) {
    @Override
    public String toString() {
        return "#" + requestId + "  " + title + " (" + status + ")";
    }
}
