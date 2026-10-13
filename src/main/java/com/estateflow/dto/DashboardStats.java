package com.estateflow.dto;

import com.estateflow.model.enums.MaintenancePriority;

import java.math.BigDecimal;
import java.util.Map;

/** Dashboard metrics (Section 9.6). Occupancy denominator is active properties only. */
public record DashboardStats(
        int totalProperties,
        int occupiedProperties,
        int vacantProperties,
        BigDecimal occupancyPercent,
        int agreementsOccupyingToday,
        int leasesEndingSoon,
        BigDecimal rentCollectedThisMonth,
        BigDecimal outstandingBalance,
        int overdueCharges,
        Map<MaintenancePriority, Integer> openMaintenanceByPriority) {
}
