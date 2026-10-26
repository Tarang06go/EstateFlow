package com.estateflow.service;

import com.estateflow.dao.jdbc.*;
import com.estateflow.db.JdbcTransactionManager;
import com.estateflow.dto.*;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.model.enums.*;
import com.estateflow.support.Fixtures;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real MySQL (estateflow_test only). Needs the v1.1-v1.5 migrations on the test schema; skips itself when v1.5 is missing.
 * Fixed clock: today is 2026-10-10.
 */
class DepositLeaseIntegrationTest extends IntegrationTestBase {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private Owner owner;

    @BeforeEach
    void requireMigration() throws Exception {
        try {
            ctx.leases().ensureReady();
            ctx.deposits().ensureReady();
        } catch (ConfigurationException e) {
            Assumptions.abort("apply db/migrations/v1.5_deposits_leases.sql to estateflow_test to run the Phase 4 integration tests");
        }
        owner = Fixtures.owner(ctx);
    }

    private String scalar(String sql) throws Exception {
        try (var h = ctx.databaseManager().acquire(); Statement st = h.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private RentalAgreement lease(LocalDate start, int months, String rent, String deposit) throws Exception {
        Property p = Fixtures.property(ctx, owner);
        Tenant t = Fixtures.tenant(ctx);
        return ctx.agreements().create(p.getPropertyId(), t.getId(), start, months, bd(rent), bd(deposit), "lease");
    }

    private DepositService depositServiceAt(String day) {
        var db = ctx.databaseManager();
        return new DepositService(new JdbcDepositDao(db), new JdbcAgreementDao(db), new JdbcTransactionManager(db), ctx.audit(),
                Clock.fixed(Instant.parse(day + "T08:00:00Z"), ZoneOffset.UTC), ctx.authorizer());
    }

    private DepositRow collect(RentalAgreement a, String amount, LocalDate date) throws Exception {
        return ctx.deposits().collect(a.getAgreementId(), bd(amount), date, PaymentMethod.BANK_TRANSFER, "REF-1", "note");
    }

    // ---------------------------------------------------------------- deposit ledger

    @Test
    void collectionsAreCappedAtTheAgreedDepositAndNeverDuplicateTheRecord() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 8, 15), 6, "10000.00", "20000.00");
        DepositRow first = collect(a, "15000.00", LocalDate.of(2026, 8, 10));
        assertEquals(DepositStatus.HELD, first.status());
        assertEquals(bd("15000.00"), first.totals().balance());
        DepositRow second = collect(a, "5000.00", LocalDate.of(2026, 8, 12));
        assertEquals(bd("20000.00"), second.totals().original());
        assertEquals("DEP-03", assertThrows(BusinessRuleException.class, () -> collect(a, "0.01", TODAY)).getRuleId());
        assertEquals("1", scalar("SELECT COUNT(*) FROM security_deposit WHERE agreement_id = " + a.getAgreementId()), "one deposit per agreement");
        assertEquals("2", scalar("SELECT COUNT(*) FROM deposit_transaction"), "the failed collection left nothing behind");
        assertThrows(com.estateflow.exception.DuplicateKeyException.class,
                () -> new JdbcDepositDao(ctx.databaseManager()).insertDeposit(a.getAgreementId()), "the database enforces one deposit per agreement");
    }

    @Test
    void deductionsAndRefundsReduceTheBalanceAndCannotOverspendIt() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 3, 1), 3, "10000.00", "20000.00"); // ended 2026-05-31
        depositServiceAt("2026-04-01").collect(a.getAgreementId(), bd("20000.00"), LocalDate.of(2026, 2, 25), PaymentMethod.UPI, null, null);

        DepositRow afterDeduction = ctx.deposits().deduct(a.getAgreementId(), bd("3500.00"), LocalDate.of(2026, 6, 2), "Wall repainting", "BILL-9", null);
        assertEquals(bd("16500.00"), afterDeduction.totals().balance());
        assertEquals(bd("3500.00"), afterDeduction.totals().deducted());

        assertEquals("DEP-04", assertThrows(BusinessRuleException.class,
                () -> ctx.deposits().deduct(a.getAgreementId(), bd("16500.01"), LocalDate.of(2026, 6, 3), "too much", null, null)).getRuleId());
        assertEquals("DEP-04", assertThrows(BusinessRuleException.class,
                () -> ctx.deposits().refund(a.getAgreementId(), bd("16500.01"), LocalDate.of(2026, 6, 3), PaymentMethod.UPI, null, null)).getRuleId());

        DepositRow partial = ctx.deposits().refund(a.getAgreementId(), bd("10000.00"), LocalDate.of(2026, 6, 5), PaymentMethod.BANK_TRANSFER, "NEFT-1", null);
        assertEquals(bd("6500.00"), partial.totals().balance());
        assertEquals(DepositStatus.HELD, partial.status());
        DepositRow full = ctx.deposits().refund(a.getAgreementId(), bd("6500.00"), LocalDate.of(2026, 6, 6), PaymentMethod.CASH, null, null);
        assertEquals(bd("0.00"), full.totals().balance());
        assertEquals(DepositStatus.SETTLED, full.status());
        assertEquals("DEP-04", assertThrows(BusinessRuleException.class,
                () -> ctx.deposits().refund(a.getAgreementId(), bd("0.01"), LocalDate.of(2026, 6, 7), PaymentMethod.CASH, null, null)).getRuleId());
        assertEquals(4, ctx.deposits().transactions(a.getAgreementId()).size(), "history keeps every entry");
    }

    @Test
    void refundsWaitForTheLeaseToEndAndDatesAreValidated() throws Exception {
        RentalAgreement active = lease(LocalDate.of(2026, 8, 15), 6, "10000.00", "20000.00"); // ends 2027-02-14
        collect(active, "20000.00", LocalDate.of(2026, 8, 10));
        assertEquals("DEP-05", assertThrows(BusinessRuleException.class, () -> ctx.deposits().refund(active.getAgreementId(), bd("100.00"), TODAY,
                PaymentMethod.CASH, null, null)).getRuleId());
        assertDoesNotThrow(() -> ctx.deposits().deduct(active.getAgreementId(), bd("100.00"), TODAY, "mid-lease damage", null, null), "deductions may happen mid-lease");
        assertThrows(ValidationException.class, () -> ctx.deposits().deduct(active.getAgreementId(), bd("10.00"), TODAY.plusDays(1), "future", null, null));
        assertEquals("DEP-09", assertThrows(BusinessRuleException.class, () -> ctx.deposits().deduct(active.getAgreementId(), bd("10.00"),
                LocalDate.of(2026, 8, 9), "before collection", null, null)).getRuleId());
        assertThrows(ValidationException.class, () -> ctx.deposits().deduct(active.getAgreementId(), bd("10.00"), TODAY, "  ", null, null));
        assertEquals("DEP-01", assertThrows(BusinessRuleException.class, () -> ctx.deposits().deduct(lease(LocalDate.of(2026, 9, 1), 3, "1000.00", "500.00")
                .getAgreementId(), bd("1.00"), TODAY, "nothing held", null, null)).getRuleId());
    }

    @Test
    void voidingKeepsHistoryRestoresTheBalanceAndProtectsAgainstNegativeBalances() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 8, 15), 6, "10000.00", "20000.00");
        collect(a, "20000.00", LocalDate.of(2026, 8, 10));
        ctx.deposits().deduct(a.getAgreementId(), bd("4000.00"), TODAY, "Damaged door", null, null);
        List<DepositTransaction> txns = ctx.deposits().transactions(a.getAgreementId());
        DepositTransaction collection = txns.get(0), deduction = txns.get(1);

        assertEquals("DEP-06", assertThrows(BusinessRuleException.class, () -> ctx.deposits().voidTransaction(collection.txnId(), "wrong")).getRuleId(),
                "cannot void a collection that has been partly used");
        DepositRow restored = ctx.deposits().voidTransaction(deduction.txnId(), "Deduction entered against the wrong tenant");
        assertEquals(bd("20000.00"), restored.totals().balance());
        assertEquals("DEP-08", assertThrows(BusinessRuleException.class, () -> ctx.deposits().voidTransaction(deduction.txnId(), "again")).getRuleId());
        assertThrows(ValidationException.class, () -> ctx.deposits().voidTransaction(collection.txnId(), " "));

        List<DepositTransaction> after = ctx.deposits().transactions(a.getAgreementId());
        assertEquals(2, after.size(), "voided entries stay in the ledger");
        assertTrue(after.get(1).isVoided());
        assertEquals("Deduction entered against the wrong tenant", after.get(1).voidReason());
        assertEquals("1", scalar("SELECT COUNT(*) FROM deposit_transaction WHERE voided_at IS NOT NULL"));
        // now the collection can be voided, which empties the deposit
        assertEquals(bd("0.00"), ctx.deposits().voidTransaction(collection.txnId(), "Collected on the wrong agreement").totals().balance());
    }

    // ---------------------------------------------------------------- authorization, audit, rollback

    @Test
    void viewerCannotChangeDepositsManagerCanAndAuditNamesTheActor() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 8, 15), 6, "10000.00", "20000.00");
        ctx.session().logout();
        ctx.auth().createInitialAdministrator("boss", "Integration-Pass-1");
        ctx.auth().login("boss", "Integration-Pass-1");
        ctx.users().createUser("mgr.dep", "Manager-Pass-12345", Role.MANAGER);
        ctx.users().createUser("view.dep", "Viewer-Pass-123456", Role.VIEWER);

        ctx.auth().logout();
        ctx.auth().login("mgr.dep", "Manager-Pass-12345");
        collect(a, "5000.00", LocalDate.of(2026, 8, 10));
        long depositId = ctx.deposits().findPosition(a.getAgreementId()).orElseThrow().depositId();
        AuditEntry entry = ctx.audit().find("DEPOSIT", depositId, 5).get(0);
        assertEquals("COLLECTED", entry.action());
        assertEquals("mgr.dep", entry.actorUsername());
        assertNotNull(entry.actorUserId());

        ctx.auth().logout();
        ctx.auth().login("view.dep", "Viewer-Pass-123456");
        long txnId = ctx.deposits().transactions(a.getAgreementId()).get(0).txnId();
        assertThrows(AuthorizationException.class, () -> collect(a, "1.00", TODAY));
        assertThrows(AuthorizationException.class, () -> ctx.deposits().deduct(a.getAgreementId(), bd("1.00"), TODAY, "x", null, null));
        assertThrows(AuthorizationException.class, () -> ctx.deposits().refund(a.getAgreementId(), bd("1.00"), TODAY, PaymentMethod.CASH, null, null));
        assertThrows(AuthorizationException.class, () -> ctx.deposits().voidTransaction(txnId, "x"));
        assertThrows(AuthorizationException.class, () -> ctx.leases().renew(a.getAgreementId(), 6, bd("1000.00"), null, true, null));
        assertThrows(AuthorizationException.class, () -> ctx.leases().extend(a.getAgreementId(), 1, null));
        assertEquals(bd("5000.00"), ctx.deposits().findPosition(a.getAgreementId()).orElseThrow().totals().balance(), "viewer can read");
        assertEquals("1", scalar("SELECT COUNT(*) FROM deposit_transaction"), "nothing was written");
        assertDoesNotThrow(() -> ctx.deposits().report(LocalDate.of(2026, 1, 1), TODAY, null, null));

        ctx.auth().logout();
        assertThrows(AuthorizationException.class, () -> ctx.deposits().findPosition(a.getAgreementId()));
        assertThrows(AuthorizationException.class, () -> ctx.leases().needingAttention());
    }

    @Test
    void anAuditFailureRollsBackTheWholeDepositChange() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 8, 15), 6, "10000.00", "20000.00");
        var db = ctx.databaseManager();
        DepositService failing = new DepositService(new JdbcDepositDao(db), new JdbcAgreementDao(db), new JdbcTransactionManager(db),
                new AuditService(new V11IntegrationTest.FailingAuditDao(), Clock.systemUTC(), ctx.authorizer()), CLOCK, ctx.authorizer());
        assertThrows(DataAccessException.class, () -> failing.collect(a.getAgreementId(), bd("100.00"), LocalDate.of(2026, 8, 10),
                PaymentMethod.CASH, null, null));
        assertEquals("0", scalar("SELECT COUNT(*) FROM security_deposit"), "the deposit record created in the same transaction is rolled back too");
        assertEquals("0", scalar("SELECT COUNT(*) FROM deposit_transaction"));
    }

    // ---------------------------------------------------------------- deposits are not income

    @Test
    void depositActivityNeverChangesRentIncomeExpensesOrNoi() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 3, 1), 3, "10000.00", "20000.00");
        List<RentCharge> charges = ctx.payments().findCharges(a.getAgreementId());
        ctx.payments().recordPayment(charges.get(0).getChargeId(), bd("10000.00"), LocalDate.of(2026, 3, 5), PaymentMethod.UPI, null, null);
        LocalDate from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 12, 31);
        ProfitabilityReport before = ctx.profitability().report(from, to, null);
        assertEquals(bd("10000.00"), before.rentReceived());

        depositServiceAt("2026-04-01").collect(a.getAgreementId(), bd("20000.00"), LocalDate.of(2026, 2, 25), PaymentMethod.CASH, null, null);
        ctx.deposits().deduct(a.getAgreementId(), bd("3000.00"), LocalDate.of(2026, 6, 2), "Cleaning", null, null);
        ctx.deposits().refund(a.getAgreementId(), bd("17000.00"), LocalDate.of(2026, 6, 4), PaymentMethod.BANK_TRANSFER, null, null);

        ProfitabilityReport after = ctx.profitability().report(from, to, null);
        assertEquals(before.rentReceived(), after.rentReceived(), "a deposit is not rent");
        assertEquals(before.rentCharged(), after.rentCharged());
        assertEquals(before.expenses(), after.expenses(), "a refund is not an expense");
        assertEquals(before.noi(), after.noi(), "deposits never enter NOI");
        assertEquals(before.months().stream().map(m -> m.income()).toList(), after.months().stream().map(m -> m.income()).toList());
        assertEquals("1", scalar("SELECT COUNT(*) FROM payment"), "no rent payment row was created for the deposit");
    }

    // ---------------------------------------------------------------- reports

    @Test
    void reportShowsLiabilityByGroupAndPeriodActivityFromLiveRecords() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 8, 15), 6, "10000.00", "20000.00");
        RentalAgreement b = lease(LocalDate.of(2026, 9, 1), 6, "9000.00", "9000.00");
        lease(LocalDate.of(2026, 9, 5), 6, "8000.00", "8000.00"); // required but not collected yet
        collect(a, "20000.00", LocalDate.of(2026, 8, 10));
        ctx.deposits().deduct(a.getAgreementId(), bd("2000.00"), LocalDate.of(2026, 10, 1), "Damage", null, null);
        collect(b, "9000.00", LocalDate.of(2026, 7, 31));

        DepositReport r = ctx.deposits().report(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, null);
        assertEquals(bd("27000.00"), r.liability(), "liability = balances held now, whatever the period");
        assertEquals(bd("20000.00"), r.collected(), "only August collections");
        assertEquals(0, r.deducted().signum(), "nothing was deducted in August");
        assertEquals(3, r.rows().size());
        assertEquals(1, r.rows().stream().filter(x -> x.status() == DepositStatus.PENDING).count());

        DepositReport october = ctx.deposits().report(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), null, null);
        assertEquals(bd("2000.00"), october.deducted());
        assertEquals(0, october.refunded().signum());

        List<DepositReport.GroupTotal> byTenant = DepositService.group(r.rows(), DepositService.Grouping.TENANT);
        assertEquals(3, byTenant.size());
        assertEquals(bd("27000.00"), byTenant.stream().map(DepositReport.GroupTotal::balance).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(3, DepositService.group(r.rows(), DepositService.Grouping.PROPERTY).size());

        Long onlyB = b.getPropertyId();
        assertEquals(bd("9000.00"), ctx.deposits().report(LocalDate.of(2026, 1, 1), TODAY, onlyB, null).liability());
        assertTrue(ctx.deposits().report(LocalDate.of(2026, 1, 1), TODAY, null, 999_999L).rows().isEmpty());
        assertThrows(ValidationException.class, () -> ctx.deposits().report(TODAY, TODAY.minusDays(1), null, null));
    }

    @Test
    void emptyDatabaseGivesEmptyReports() throws Exception {
        DepositReport r = ctx.deposits().report(LocalDate.of(2026, 1, 1), TODAY, null, null);
        assertEquals(0, r.liability().signum());
        assertTrue(r.rows().isEmpty() && r.activity().isEmpty());
        assertTrue(ctx.leases().needingAttention().isEmpty());
        assertTrue(ctx.leases().endingWithin(90).isEmpty());
        assertTrue(DepositService.group(r.rows(), DepositService.Grouping.AGREEMENT).isEmpty());
    }

    // ---------------------------------------------------------------- renewal

    @Test
    void renewalCreatesALinkedAgreementCarriesTheDepositAndPreservesTheOriginal() throws Exception {
        RentalAgreement old = lease(LocalDate.of(2026, 8, 15), 3, "10000.00", "20000.00"); // ends 2026-11-14
        collect(old, "20000.00", LocalDate.of(2026, 8, 10));
        ctx.deposits().deduct(old.getAgreementId(), bd("1500.00"), TODAY, "Minor damage", null, null);
        BigDecimal liabilityBefore = ctx.deposits().report(LocalDate.of(2026, 1, 1), TODAY, null, null).liability();

        RentalAgreement next = ctx.leases().renew(old.getAgreementId(), 6, bd("11000.00"), bd("20000.00"), true, "Annual review");
        assertEquals(LocalDate.of(2026, 11, 15), next.getStartDate(), "starts the day after the old lease ends");
        assertEquals(LocalDate.of(2027, 5, 14), next.getEndDate());
        assertEquals(bd("11000.00"), next.getMonthlyRent());
        assertEquals(6, ctx.payments().findCharges(next.getAgreementId()).size());
        assertTrue(ctx.payments().findCharges(next.getAgreementId()).stream().allMatch(c -> c.getAmountDue().compareTo(bd("11000.00")) == 0));

        RentalAgreement oldAfter = ctx.agreements().get(old.getAgreementId());
        assertEquals(old.getStartDate(), oldAfter.getStartDate());
        assertEquals(old.getEndDate(), oldAfter.getEndDate());
        assertEquals(bd("10000.00"), oldAfter.getMonthlyRent(), "the original agreement is untouched");
        assertEquals(AgreementStatus.ACTIVE, oldAfter.getStatus());
        assertEquals(next.getAgreementId(), oldAfter.getRenewedByAgreementId());
        assertEquals(EffectiveAgreementStatus.RENEWED, ctx.agreements().effectiveStatus(oldAfter));
        assertEquals(old.getAgreementId(), ctx.agreements().get(next.getAgreementId()).getRenewedFromAgreementId());
        assertEquals(3, ctx.payments().findCharges(old.getAgreementId()).size());

        // deposit moved by ledger transfer: nothing created or lost
        DepositRow oldRow = ctx.deposits().findPosition(old.getAgreementId()).orElseThrow();
        DepositRow newRow = ctx.deposits().findPosition(next.getAgreementId()).orElseThrow();
        assertEquals(bd("0.00"), oldRow.totals().balance());
        assertEquals(bd("18500.00"), newRow.totals().balance());
        assertEquals(bd("18500.00"), newRow.totals().transferredIn());
        assertEquals(liabilityBefore, ctx.deposits().report(LocalDate.of(2026, 1, 1), TODAY, null, null).liability(), "liability unchanged by a renewal");
        DepositReport period = ctx.deposits().report(LocalDate.of(2026, 10, 1), TODAY, null, null);
        assertEquals(0, period.collected().signum(), "a carry-over is not a new collection");
        assertEquals(bd("1500.00"), period.deducted());

        // history and audit record who and what
        List<LeaseChange> history = ctx.leases().history(next.getAgreementId());
        assertEquals(1, history.size());
        LeaseChange c = history.get(0);
        assertEquals("RENEWED", c.changeType());
        assertEquals(old.getAgreementId(), c.relatedAgreementId());
        assertEquals(LocalDate.of(2026, 11, 14), c.oldEndDate());
        assertEquals(bd("10000.00"), c.oldMonthlyRent());
        assertEquals(LocalDate.of(2027, 5, 14), c.newEndDate());
        assertEquals(bd("11000.00"), c.newMonthlyRent());
        assertEquals("it-admin", c.actorUsername());
        assertEquals(1, ctx.leases().history(old.getAgreementId()).size(), "the old lease shows its renewal too");
        AuditEntry audit = ctx.audit().find("AGREEMENT", next.getAgreementId(), 5).get(0);
        assertEquals("RENEWED", audit.action());
        assertEquals("it-admin", audit.actorUsername());

        // cannot be renewed twice
        assertTrue(ctx.leases().whyNotRenewable(old.getAgreementId()).orElseThrow().contains("already been renewed"));
        assertEquals("LR-01", assertThrows(BusinessRuleException.class, () -> ctx.leases().renew(old.getAgreementId(), 6, bd("11000.00"), null, true, null)).getRuleId());
        assertEquals("1", scalar("SELECT COUNT(*) FROM rental_agreement WHERE renewed_from_agreement_id = " + old.getAgreementId()));
    }

    @Test
    void renewalIsRefusedForIneligibleOrOverlappingAgreements() throws Exception {
        // terminated
        RentalAgreement terminated = lease(LocalDate.of(2026, 8, 15), 6, "10000.00", "0.00");
        ctx.agreements().terminate(terminated.getAgreementId(), LocalDate.of(2026, 10, 20));
        assertEquals("LR-01", assertThrows(BusinessRuleException.class, () -> ctx.leases().renew(terminated.getAgreementId(), 6, bd("1000.00"), null, false, null)).getRuleId());
        assertEquals("LR-01", assertThrows(BusinessRuleException.class, () -> ctx.leases().extend(terminated.getAgreementId(), 1, null)).getRuleId());
        // not started yet
        RentalAgreement upcoming = lease(LocalDate.of(2026, 12, 1), 6, "10000.00", "0.00");
        assertTrue(ctx.leases().whyNotRenewable(upcoming.getAgreementId()).orElseThrow().contains("not started"));
        // ended more than 30 days ago (grace boundary): ended 2026-09-10 = 30 days ago is allowed, 2026-09-09 is not
        RentalAgreement justInGrace = lease(LocalDate.of(2026, 6, 11), 3, "10000.00", "0.00"); // ends 2026-09-10
        assertTrue(ctx.leases().whyNotRenewable(justInGrace.getAgreementId()).isEmpty());
        RentalAgreement tooOld = lease(LocalDate.of(2026, 6, 10), 3, "10000.00", "0.00");      // ends 2026-09-09
        assertTrue(ctx.leases().whyNotRenewable(tooOld.getAgreementId()).orElseThrow().contains("more than 30 days"));
        assertEquals("LR-01", assertThrows(BusinessRuleException.class, () -> ctx.leases().renew(tooOld.getAgreementId(), 6, bd("1000.00"), null, false, null)).getRuleId());
        // overlap with an agreement already booked after this one
        Property p = Fixtures.property(ctx, owner);
        Tenant t1 = Fixtures.tenant(ctx), t2 = Fixtures.tenant(ctx);
        RentalAgreement current = ctx.agreements().create(p.getPropertyId(), t1.getId(), LocalDate.of(2026, 8, 15), 3, bd("10000.00"), bd("0.00"), null); // ends 11-14
        ctx.agreements().create(p.getPropertyId(), t2.getId(), LocalDate.of(2026, 11, 20), 6, bd("10000.00"), bd("0.00"), null);
        assertEquals("BR-13", assertThrows(BusinessRuleException.class, () -> ctx.leases().renew(current.getAgreementId(), 6, bd("10000.00"), null, false, null)).getRuleId());
        assertEquals("BR-13", assertThrows(BusinessRuleException.class, () -> ctx.leases().extend(current.getAgreementId(), 2, null)).getRuleId());
        assertEquals("0", scalar("SELECT COUNT(*) FROM rental_agreement WHERE renewed_from_agreement_id IS NOT NULL"), "refusals leave no half-made renewal");
    }

    @Test
    void renewalInputsAreValidatedAndAnOversizedDepositCarryIsRefused() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 8, 15), 3, "10000.00", "20000.00");
        collect(a, "20000.00", LocalDate.of(2026, 8, 10));
        assertThrows(ValidationException.class, () -> ctx.leases().renew(a.getAgreementId(), 0, bd("1000.00"), null, false, null));
        assertThrows(ValidationException.class, () -> ctx.leases().renew(a.getAgreementId(), 121, bd("1000.00"), null, false, null));
        assertThrows(ValidationException.class, () -> ctx.leases().renew(a.getAgreementId(), 6, bd("0.00"), null, false, null));
        assertThrows(ValidationException.class, () -> ctx.leases().renew(a.getAgreementId(), 6, bd("-5.00"), null, false, null));
        assertThrows(ValidationException.class, () -> ctx.leases().renew(a.getAgreementId(), 6, bd("1000.001"), null, false, null));
        assertThrows(ValidationException.class, () -> ctx.leases().renew(a.getAgreementId(), 6, bd("1000.00"), bd("-1.00"), false, null));
        assertEquals("LR-05", assertThrows(BusinessRuleException.class, () -> ctx.leases().renew(a.getAgreementId(), 6, bd("1000.00"), bd("5000.00"), true, null)).getRuleId());
        assertEquals("0", scalar("SELECT COUNT(*) FROM rental_agreement WHERE renewed_from_agreement_id IS NOT NULL"));
        assertEquals(bd("20000.00"), ctx.deposits().findPosition(a.getAgreementId()).orElseThrow().totals().balance(), "no deposit moved");
        // without carry-over the deposit stays where it is
        RentalAgreement next = ctx.leases().renew(a.getAgreementId(), 6, bd("1000.00"), bd("5000.00"), false, null);
        assertEquals(bd("20000.00"), ctx.deposits().findPosition(a.getAgreementId()).orElseThrow().totals().balance());
        assertEquals(DepositStatus.PENDING, ctx.deposits().findPosition(next.getAgreementId()).orElseThrow().status());
    }

    // ---------------------------------------------------------------- extension

    @Test
    void extensionAddsRentPeriodsKeepsExistingOnesAndRecordsTheOldEndDate() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 8, 15), 3, "10000.00", "0.00"); // ends 2026-11-14
        List<RentCharge> before = ctx.payments().findCharges(a.getAgreementId());
        RentalAgreement extended = ctx.leases().extend(a.getAgreementId(), 3, "Tenant asked for 3 more months");
        assertEquals(6, extended.getDurationMonths());
        assertEquals(LocalDate.of(2027, 2, 14), extended.getEndDate());
        assertEquals(bd("10000.00"), extended.getMonthlyRent(), "extension keeps the rent");
        List<RentCharge> afterCharges = ctx.payments().findCharges(a.getAgreementId());
        assertEquals(6, afterCharges.size());
        assertEquals(before.stream().map(RentCharge::getChargeId).toList(), afterCharges.subList(0, 3).stream().map(RentCharge::getChargeId).toList(),
                "existing rent periods are untouched");
        assertEquals(LocalDate.of(2027, 2, 14), extended.getEffectiveEndDate());

        LeaseChange change = ctx.leases().history(a.getAgreementId()).get(0);
        assertEquals("EXTENDED", change.changeType());
        assertEquals(LocalDate.of(2026, 11, 14), change.oldEndDate());
        assertEquals(LocalDate.of(2027, 2, 14), change.newEndDate());
        assertEquals(change.oldMonthlyRent(), change.newMonthlyRent());
        assertEquals("it-admin", change.actorUsername());
        assertEquals("Tenant asked for 3 more months", change.note());
        AuditEntry audit = ctx.audit().find("AGREEMENT", a.getAgreementId(), 5).get(0);
        assertEquals("EXTENDED", audit.action());
        assertTrue(audit.oldValue().contains("2026-11-14") && audit.newValue().contains("2027-02-14"));
    }

    @Test
    void extensionLimitsAreEnforced() throws Exception {
        RentalAgreement longLease = lease(LocalDate.of(2026, 8, 15), 118, "10000.00", "0.00");
        assertThrows(ValidationException.class, () -> ctx.leases().extend(longLease.getAgreementId(), 0, null));
        assertEquals("LR-07", assertThrows(BusinessRuleException.class, () -> ctx.leases().extend(longLease.getAgreementId(), 3, null)).getRuleId());
        assertEquals(120, ctx.leases().extend(longLease.getAgreementId(), 2, null).getDurationMonths(), "exactly 120 months is allowed");
        RentalAgreement ended = lease(LocalDate.of(2026, 3, 1), 3, "10000.00", "0.00");
        assertTrue(ctx.leases().whyNotExtendable(ended.getAgreementId()).orElseThrow().contains("Renew"));
        assertEquals("LR-01", assertThrows(BusinessRuleException.class, () -> ctx.leases().extend(ended.getAgreementId(), 1, null)).getRuleId());
        // the last day of the lease can still be extended; the day after cannot
        RentalAgreement lastDay = lease(LocalDate.of(2026, 7, 11), 3, "10000.00", "0.00"); // ends 2026-10-10 = today
        assertTrue(ctx.leases().whyNotExtendable(lastDay.getAgreementId()).isEmpty());
        RentalAgreement dayAfter = lease(LocalDate.of(2026, 7, 10), 3, "10000.00", "0.00"); // ends 2026-10-09
        assertTrue(ctx.leases().whyNotExtendable(dayAfter.getAgreementId()).isPresent());
    }

    // ---------------------------------------------------------------- expiry reports

    @Test
    void expiryWindowsAreInclusiveAndRenewedLeasesAreNotListed() throws Exception {
        RentalAgreement in30 = lease(LocalDate.of(2026, 10, 10), 1, "5000.00", "0.00");  // ends 2026-11-09 = today + 30
        RentalAgreement in31 = lease(LocalDate.of(2026, 10, 11), 1, "5000.00", "0.00");  // ends 2026-11-10 = today + 31
        RentalAgreement in90 = lease(LocalDate.of(2026, 10, 10), 4, "5000.00", "0.00");  // ends 2027-02-09 = today + 122
        List<Long> w30 = ctx.leases().endingWithin(30).stream().map(ExpiringLeaseRow::agreementId).toList();
        assertTrue(w30.contains(in30.getAgreementId()));
        assertFalse(w30.contains(in31.getAgreementId()));
        assertEquals(List.of(in30.getAgreementId()), w30);
        List<Long> w31 = ctx.leases().endingWithin(31).stream().map(ExpiringLeaseRow::agreementId).toList();
        assertTrue(w31.contains(in31.getAgreementId()));
        assertFalse(ctx.leases().endingWithin(90).stream().anyMatch(r -> r.agreementId() == in90.getAgreementId()));
        assertThrows(ValidationException.class, () -> ctx.leases().endingWithin(0));
        assertThrows(ValidationException.class, () -> ctx.leases().endingWithin(366));
        assertDoesNotThrow(() -> ctx.leases().endingWithin(365));

        ctx.leases().renew(in30.getAgreementId(), 6, bd("5000.00"), null, false, null);
        assertFalse(ctx.leases().endingWithin(60).stream().anyMatch(r -> r.agreementId() == in30.getAgreementId()),
                "a renewed lease is no longer 'expiring'");
        assertEquals(AgreementFilter.RENEWED, AgreementFilter.valueOf("RENEWED"));
        assertTrue(ctx.agreements().searchRows("", AgreementFilter.RENEWED).stream().anyMatch(r -> r.agreementId() == in30.getAgreementId()));
        assertFalse(ctx.agreements().searchRows("", AgreementFilter.EXPIRED).stream().anyMatch(r -> r.agreementId() == in30.getAgreementId()));
    }

    @Test
    void endedLeasesNeedAttentionUntilRentIsPaidAndTheDepositIsSettled() throws Exception {
        RentalAgreement a = lease(LocalDate.of(2026, 3, 1), 3, "10000.00", "20000.00"); // ended 2026-05-31
        depositServiceAt("2026-04-01").collect(a.getAgreementId(), bd("20000.00"), LocalDate.of(2026, 2, 25), PaymentMethod.CASH, null, null);
        List<RentCharge> charges = ctx.payments().findCharges(a.getAgreementId());
        for (RentCharge c : charges) {
            ctx.payments().recordPayment(c.getChargeId(), c.getAmountDue(), LocalDate.of(2026, 5, 31), PaymentMethod.CASH, null, null);
        }
        // rent fully paid but deposit still held: needs attention
        List<LeaseAttentionRow> rows = ctx.leases().needingAttention();
        assertEquals(1, rows.size());
        LeaseAttentionRow row = rows.get(0);
        assertEquals(a.getAgreementId(), row.agreementId());
        assertEquals(bd("20000.00"), row.depositBalance());
        assertEquals(bd("0.00"), row.rentOutstanding());
        assertEquals(LocalDate.of(2026, 5, 31), row.effectiveEnd());
        assertEquals(132, row.daysSinceEnd());
        // settle the deposit: no longer listed
        ctx.deposits().refund(a.getAgreementId(), bd("20000.00"), TODAY, PaymentMethod.BANK_TRANSFER, null, null);
        assertTrue(ctx.leases().needingAttention().isEmpty());
        // an ended lease with unpaid rent is listed; active leases never are
        RentalAgreement unpaid = lease(LocalDate.of(2026, 4, 1), 2, "7000.00", "0.00");
        lease(LocalDate.of(2026, 8, 15), 6, "10000.00", "0.00");
        List<LeaseAttentionRow> unpaidRows = ctx.leases().needingAttention();
        assertEquals(List.of(unpaid.getAgreementId()), unpaidRows.stream().map(LeaseAttentionRow::agreementId).toList());
        assertEquals(bd("14000.00"), unpaidRows.get(0).rentOutstanding());
    }
}
