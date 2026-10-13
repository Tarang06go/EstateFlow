package com.estateflow.dto;

import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.model.enums.MaintenanceStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Maintenance analytics. Costs only count requests that are IN_PROGRESS or RESOLVED (BR-32).
 * averageResolutionDays is null when nothing has been resolved yet.
 */
public record MaintenanceSummary(
        Map<MaintenanceStatus, Integer> countByStatus,
        Map<MaintenancePriority, Integer> openByPriority,
        BigDecimal resolvedCost, BigDecimal inProgressCost, BigDecimal averageResolutionDays,
        List<PropertyCost> costByProperty, List<MonthlyAmount> costByMonth) {

    public BigDecimal totalCost() {
        return resolvedCost.add(inProgressCost);
    }

    public int totalRequests() {
        return countByStatus.values().stream().mapToInt(Integer::intValue).sum();
    }
}
