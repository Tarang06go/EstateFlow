package com.estateflow.dto;

import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.model.enums.MaintenanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaintenanceReportRow(
        long requestId, long propertyId, String propertyName, String tenantName, String title,
        MaintenancePriority priority, MaintenanceStatus status, LocalDate reportedDate,
        LocalDate resolvedDate, String assignedTo, BigDecimal cost) {
}
