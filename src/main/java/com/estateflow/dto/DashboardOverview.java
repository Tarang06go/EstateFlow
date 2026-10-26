package com.estateflow.dto;

import java.util.List;

/**
 * Everything the dashboard shows, loaded in one service call. "Expected" is the sum of live rent charges
 * due in a month; "collected" is the sum of valid payments dated in that month (they may belong to earlier
 * charges, so the two are comparable but not identical flows). All lists are capped.
 */
public record DashboardOverview(
        DashboardStats stats, int months, List<MonthlyAmount> collected, List<MonthlyAmount> expected,
        List<MonthlyAmount> maintenanceCosts, List<RentReminderRow> overdue, List<ExpiringLeaseRow> expiring,
        List<PropertyOccupancyRow> vacant, List<MaintenanceReportRow> openMaintenance, List<AuditEntry> recentActivity) {
}
