package com.estateflow.dto;

import java.util.List;

/** Chart series for the dashboard: last six calendar months, zero-filled. Occupancy comes from DashboardStats. */
public record DashboardCharts(List<MonthlyAmount> rentCollected, List<MonthlyAmount> maintenanceCosts) {
}
