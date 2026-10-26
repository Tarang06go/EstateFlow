package com.estateflow.service;

import com.estateflow.model.Property;
import com.estateflow.model.RentalAgreement;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.support.Fixtures;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** MySQL-backed checks for allocation, voids, period boundaries, arrears bands, and charge reconciliation. */
class RentCollectionInsightsIntegrationTest extends IntegrationTestBase {
    @Test void partialPaymentsVoidsDateBoundariesAndEarlierPeriodReceiptsReconcile() throws Exception {
        var owner = Fixtures.owner(ctx);
        Property property = Fixtures.property(ctx, owner);
        var tenant = Fixtures.tenant(ctx);
        RentalAgreement agreement = ctx.agreements().create(property.getPropertyId(), tenant.getId(),
                LocalDate.of(2026, 7, 1), 4, new BigDecimal("100.00"), BigDecimal.ZERO, null);
        var charges = ctx.payments().findCharges(agreement.getAgreementId());
        assertEquals(4, charges.size());
        long september = charges.get(2).getChargeId();
        long october = charges.get(3).getChargeId();

        ctx.payments().recordPayment(september, new BigDecimal("50.00"), LocalDate.of(2026, 9, 30), PaymentMethod.CASH, null, null);
        ctx.payments().recordPayment(september, new BigDecimal("50.00"), LocalDate.of(2026, 10, 5), PaymentMethod.UPI, null, null);
        ctx.payments().recordPayment(october, new BigDecimal("40.00"), TODAY, PaymentMethod.UPI, null, null);
        var voided = ctx.payments().recordPayment(october, new BigDecimal("60.00"), TODAY, PaymentMethod.CASH, null, null);
        ctx.payments().voidPayment(voided.getPaymentId(), "Duplicate receipt entered in test");

        var report = ctx.collectionInsights().report(LocalDate.of(2026, 10, 1), TODAY, property.getPropertyId());
        assertEquals(1, report.totals().chargeCount());
        assertEquals(new BigDecimal("100.00"), report.totals().chargesDue());
        assertEquals(new BigDecimal("40.00"), report.totals().collectedOnSelectedCharges());
        assertEquals(new BigDecimal("60.00"), report.totals().outstandingOnSelectedCharges());
        assertEquals(new BigDecimal("40.00"), report.totals().collectionPercent());
        assertEquals(new BigDecimal("90.00"), report.totals().paymentsReceivedInPeriod());
        assertEquals(new BigDecimal("50.00"), report.totals().paymentsAppliedToEarlierCharges());
        assertEquals(1, report.properties().size());
        assertEquals(new BigDecimal("60.00"), report.properties().getFirst().outstanding());
        assertEquals(1, report.tenants().size());
        assertEquals(new BigDecimal("40.00"), report.tenants().getFirst().collected());
        assertEquals(1, report.monthlyTrend().size());
        assertEquals(new BigDecimal("90.00"), report.monthlyTrend().getFirst().paymentsReceived());

        var history = ctx.collectionInsights().report(LocalDate.of(2026, 9, 1), TODAY, property.getPropertyId());
        assertEquals(1, history.paymentBehavior().size());
        assertEquals(1, history.paymentBehavior().getFirst().fullySettledCharges());
        assertEquals(1, history.paymentBehavior().getFirst().lateCharges());
        assertFalse(history.paymentBehavior().getFirst().sufficientHistory());
        assertFalse(history.paymentBehavior().getFirst().repeatedLatePattern());
    }

    @Test void arrearsAgeBandsIncludeExactBoundariesAndCountPartialBalancesOnce() throws Exception {
        createOverdueCharge(LocalDate.of(2026, 9, 10)); // 30 days
        createOverdueCharge(LocalDate.of(2026, 9, 9));  // 31 days
        createOverdueCharge(LocalDate.of(2026, 7, 12)); // 90 days
        createOverdueCharge(LocalDate.of(2026, 7, 11)); // 91 days
        var report = ctx.collectionInsights().report(TODAY, TODAY, null);
        assertEquals(4, report.arrears().size());
        assertEquals("1-30 days", report.arrears().get(0).label());
        assertEquals(1, report.arrears().get(0).chargeCount());
        assertEquals("31-60 days", report.arrears().get(1).label());
        assertEquals("61-90 days", report.arrears().get(2).label());
        assertEquals("Over 90 days", report.arrears().get(3).label());
        assertTrue(report.followUp().stream().allMatch(row -> row.balance().signum() > 0));
    }

    @Test void cancelledChargesAreExcludedAndEmptySelectionHasZeroRate() throws Exception {
        var empty = ctx.collectionInsights().report(TODAY, TODAY, null);
        assertEquals(0, empty.totals().chargeCount());
        assertEquals(BigDecimal.ZERO, empty.totals().collectionPercent());
        assertEquals(BigDecimal.ZERO, empty.totals().outstandingOnSelectedCharges());

        var owner = Fixtures.owner(ctx);
        var property = Fixtures.property(ctx, owner);
        var tenant = Fixtures.tenant(ctx);
        var agreement = ctx.agreements().create(property.getPropertyId(), tenant.getId(),
                LocalDate.of(2026, 9, 1), 2, new BigDecimal("100.00"), BigDecimal.ZERO, null);
        ctx.agreements().terminate(agreement.getAgreementId(), LocalDate.of(2026, 9, 30));
        var cancelled = ctx.collectionInsights().report(LocalDate.of(2026, 10, 1), TODAY, property.getPropertyId());
        assertEquals(0, cancelled.totals().chargeCount());
        assertTrue(cancelled.followUp().isEmpty());
    }

    private void createOverdueCharge(LocalDate dueDate) throws Exception {
        var owner = Fixtures.owner(ctx);
        var property = Fixtures.property(ctx, owner);
        var tenant = Fixtures.tenant(ctx);
        ctx.agreements().create(property.getPropertyId(), tenant.getId(), dueDate, 1,
                new BigDecimal("100.00"), BigDecimal.ZERO, null);
    }
}
