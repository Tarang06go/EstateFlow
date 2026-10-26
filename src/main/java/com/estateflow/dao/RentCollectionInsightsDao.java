package com.estateflow.dao;

import com.estateflow.dto.RentCollectionInsightsReport.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Read-only rent-collection aggregates; payments are reconciled to their rent charge exactly once. */
public interface RentCollectionInsightsDao {
    CollectionTotals totals(LocalDate from, LocalDate to, Long propertyId);
    List<MonthlyTrend> monthlyTrend(LocalDate from, LocalDate to, Long propertyId);
    List<PropertyBreakdown> byProperty(LocalDate from, LocalDate to, Long propertyId);
    List<TenantBreakdown> byTenant(LocalDate from, LocalDate to, Long propertyId);
    List<ArrearsBand> arrears(LocalDate asOf, Long propertyId);
    List<ChargeFollowUp> followUp(LocalDate asOf, Long propertyId, boolean includeTenantName);
    List<LatePaymentBehavior> paymentBehavior(LocalDate from, LocalDate to, Long propertyId, boolean includeTenantName);
}
