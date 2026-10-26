package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Actual portfolio metrics plus a separately labeled, simple run-rate forecast. */
public record AdvancedAnalyticsReport(
        LocalDate from,
        LocalDate to,
        CollectionMetrics collection,
        OccupancySummary occupancy,
        List<ArrearsBucket> arrears,
        ProfitabilityReport profitability,
        List<ForecastMonth> forecast,
        LeaseExpirySummary leaseExpiry,
        MaintenanceSummary maintenance,
        boolean vendorAnalyticsAvailable,
        List<VendorPerformance> vendorPerformance) {

    public record CollectionMetrics(int chargeCount, int fullyCollectedCount, BigDecimal billed,
                                    BigDecimal collectedToDate, BigDecimal outstanding, BigDecimal ratePercent) { }

    public record OccupancySummary(int activeProperties, int occupiedProperties, int vacantProperties,
                                   BigDecimal percent) { }

    public record ArrearsBucket(String label, int charges, BigDecimal balance) { }

    public record ForecastMonth(YearMonth month, BigDecimal estimatedRentReceived,
                                BigDecimal estimatedOperatingExpenses, BigDecimal estimatedNoi) { }

    public record LeaseExpirySummary(int warningDays, int leasesEnding, BigDecimal monthlyRentAtRisk,
                                     List<LeaseExpiryMonth> byMonth) { }

    public record LeaseExpiryMonth(YearMonth month, int leasesEnding, BigDecimal monthlyRentAtRisk) { }

    public record VendorPerformance(long vendorId, String vendorName, int completedOrders,
                                    int onTimeOrders, int dueDatedOrders, BigDecimal onTimePercent, BigDecimal actualCost,
                                    BigDecimal averageDaysToComplete) { }
}
