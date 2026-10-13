package com.estateflow.service;

import com.estateflow.exception.BusinessRuleException;
import com.estateflow.model.*;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.support.Fixtures;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Section 3.4 rule 10: latch-coordinated two-thread races for the two locked flows. */
class ConcurrencyIntegrationTest extends IntegrationTestBase {

    /** Runs all tasks at the same moment; returns each outcome (the value, or the thrown exception). */
    private static <T> List<Object> race(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return results;
    }

    @RepeatedTest(5)
    @DisplayName("BR-13: two threads creating overlapping agreements for one property -> exactly one wins")
    void overlappingAgreementCreation_exactlyOneSucceeds() throws Exception {
        Property property = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant t1 = Fixtures.tenant(ctx);
        Tenant t2 = Fixtures.tenant(ctx);
        List<Callable<RentalAgreement>> tasks = List.of(
                () -> ctx.agreements().create(property.getPropertyId(), t1.getId(), LocalDate.of(2026, 11, 1), 6,
                        new BigDecimal("1000.00"), null, null),
                () -> ctx.agreements().create(property.getPropertyId(), t2.getId(), LocalDate.of(2026, 12, 1), 6,
                        new BigDecimal("1000.00"), null, null));
        List<Object> results = race(tasks);

        long successes = results.stream().filter(r -> r instanceof RentalAgreement).count();
        assertEquals(1, successes, "results: " + results);
        Object loser = results.stream().filter(r -> !(r instanceof RentalAgreement)).findFirst().orElseThrow();
        assertInstanceOf(BusinessRuleException.class, loser);
        assertEquals("BR-13", ((BusinessRuleException) loser).getRuleId());
        assertEquals(1, ctx.agreements().findByProperty(property.getPropertyId()).size());
        RentalAgreement winner = (RentalAgreement) results.stream().filter(r -> r instanceof RentalAgreement).findFirst().orElseThrow();
        assertEquals(6, ctx.payments().findCharges(winner.getAgreementId()).size());
    }

    @RepeatedTest(5)
    @DisplayName("BR-21: four threads paying 40 against a balance of 100 -> exactly two succeed, never overpaid")
    void competingPayments_neverOverpayTheCharge() throws Exception {
        Property property = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant tenant = Fixtures.tenant(ctx);
        RentalAgreement a = ctx.agreements().create(property.getPropertyId(), tenant.getId(),
                LocalDate.of(2026, 10, 1), 1, new BigDecimal("100.00"), null, null);
        long chargeId = ctx.payments().findCharges(a.getAgreementId()).get(0).getChargeId();

        List<Callable<Payment>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> ctx.payments().recordPayment(chargeId, new BigDecimal("40.00"), TODAY,
                    PaymentMethod.UPI, null, null));
        }
        List<Object> results = race(tasks);

        assertEquals(2, results.stream().filter(r -> r instanceof Payment).count(), "results: " + results);
        results.stream().filter(r -> !(r instanceof Payment)).forEach(r -> {
            assertInstanceOf(BusinessRuleException.class, r);
            assertEquals("BR-21", ((BusinessRuleException) r).getRuleId());
        });
        RentCharge charge = ctx.payments().findCharges(a.getAgreementId()).get(0);
        assertEquals(new BigDecimal("80.00"), charge.getAmountPaid());
        assertTrue(charge.getBalance().signum() >= 0);
    }

    @Test
    @DisplayName("BR-21: two threads each paying the full balance -> one succeeds, charge is exactly paid")
    void duplicateSave_fullBalance_onlyOnePayment() throws Exception {
        Property property = Fixtures.property(ctx, Fixtures.owner(ctx));
        Tenant tenant = Fixtures.tenant(ctx);
        RentalAgreement a = ctx.agreements().create(property.getPropertyId(), tenant.getId(),
                LocalDate.of(2026, 10, 1), 1, new BigDecimal("100.00"), null, null);
        long chargeId = ctx.payments().findCharges(a.getAgreementId()).get(0).getChargeId();
        Callable<Payment> pay = () -> ctx.payments().recordPayment(chargeId, new BigDecimal("100.00"), TODAY,
                PaymentMethod.CASH, null, null);
        List<Object> results = race(List.of(pay, pay));
        assertEquals(1, results.stream().filter(r -> r instanceof Payment).count());
        assertEquals(1, ctx.payments().findPayments(chargeId).size());
        assertEquals(0, ctx.payments().findCharges(a.getAgreementId()).get(0).getBalance().signum());
    }
}
