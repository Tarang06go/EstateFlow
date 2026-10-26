package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Rent-collection facts for a selected due-date period, plus current arrears and observed payment behavior. */
public record RentCollectionInsightsReport(LocalDate from, LocalDate to, LocalDate asOf,
        Long propertyId, CollectionTotals totals, List<MonthlyTrend> monthlyTrend,
        List<PropertyBreakdown> properties, List<TenantBreakdown> tenants,
        List<ArrearsBand> arrears, List<ChargeFollowUp> followUp,
        List<LatePaymentBehavior> paymentBehavior, LocalDate behaviorFrom, LocalDate behaviorTo) {

    public record CollectionTotals(int chargeCount, BigDecimal chargesDue,
            BigDecimal collectedOnSelectedCharges, BigDecimal outstandingOnSelectedCharges,
            BigDecimal collectionPercent, BigDecimal paymentsReceivedInPeriod,
            BigDecimal paymentsAppliedToEarlierCharges) { }

    public record MonthlyTrend(YearMonth month, BigDecimal chargesDue,
            BigDecimal collectedOnSelectedCharges, BigDecimal outstanding,
            BigDecimal paymentsReceived) { }

    public record PropertyBreakdown(long propertyId, String propertyName, int chargeCount,
            BigDecimal chargesDue, BigDecimal collected, BigDecimal outstanding,
            BigDecimal paymentsReceived) { }

    public record TenantBreakdown(long tenantId, String tenantName, String propertyName,
            int chargeCount, BigDecimal chargesDue, BigDecimal collected,
            BigDecimal outstanding, BigDecimal paymentsReceived) { }

    public record ArrearsBand(String label, int chargeCount, BigDecimal balance) { }

    /** Contains tenant name only for roles granted VIEW_TENANT_INSIGHTS. */
    public record ChargeFollowUp(long chargeId, String propertyName, String tenantName,
            LocalDate dueDate, long daysOverdue, BigDecimal amountDue,
            BigDecimal amountPaid, BigDecimal balance) { }

    /** Counts fully settled charges only; this is descriptive history, not a tenant score. */
    public record LatePaymentBehavior(long tenantId, String tenantName, String propertyName,
            int fullySettledCharges, int lateCharges, boolean sufficientHistory,
            boolean repeatedLatePattern) { }
}
