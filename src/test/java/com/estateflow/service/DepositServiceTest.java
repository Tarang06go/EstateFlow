package com.estateflow.service;

import com.estateflow.dao.AgreementDao;
import com.estateflow.dto.DepositReport;
import com.estateflow.dto.DepositRow;
import com.estateflow.exception.*;
import com.estateflow.model.DepositTransaction;
import com.estateflow.model.RentalAgreement;
import com.estateflow.model.enums.*;
import com.estateflow.security.Authorizer;
import com.estateflow.support.FakeAuditDao;
import com.estateflow.support.FakeDepositDao;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepositServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private final AgreementDao agreementDao = mock(AgreementDao.class);
    private FakeDepositDao depositDao;
    private FakeAuditDao auditDao;
    private DepositService service;

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private DepositService serviceFor(Authorizer auth) {
        return new DepositService(depositDao, agreementDao, new PassThroughTransactionManager(), new AuditService(auditDao, CLOCK, auth), CLOCK, auth);
    }

    private RentalAgreement agreement(long id, LocalDate start, LocalDate end, String deposit) {
        RentalAgreement a = new RentalAgreement();
        a.setAgreementId(id);
        a.setPropertyId(10L + id);
        a.setTenantId(20L + id);
        a.setStartDate(start);
        a.setEndDate(end);
        a.setDurationMonths(6);
        a.setMonthlyRent(bd("10000.00"));
        a.setSecurityDeposit(bd(deposit));
        a.setStatus(AgreementStatus.ACTIVE);
        depositDao.agreements.put(id, a);
        when(agreementDao.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    @BeforeEach
    void setUp() {
        depositDao = new FakeDepositDao();
        auditDao = new FakeAuditDao();
        service = serviceFor(TestAuth.admin());
        agreement(1, LocalDate.of(2026, 8, 15), LocalDate.of(2027, 2, 14), "20000.00");   // active
        agreement(2, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31), "20000.00");    // ended
    }

    private DepositRow collect1(String amount) throws Exception {
        return service.collect(1, bd(amount), LocalDate.of(2026, 8, 10), PaymentMethod.UPI, "R1", null);
    }

    /** Deposit on the ended agreement #2, collected while it was running. */
    private void seedEnded() throws Exception {
        long id = depositDao.insertDeposit(2);
        depositDao.insertTransaction(new DepositTransaction(null, id, DepositType.COLLECTION, bd("20000.00"), LocalDate.of(2026, 2, 25),
                PaymentMethod.CASH, null, null, null, null, null, null, null));
    }

    // ---- collect

    @Test
    void firstCollectionCreatesTheDepositAndAuditsTheActor() throws Exception {
        DepositRow row = collect1("15000.00");
        assertEquals(DepositStatus.HELD, row.status());
        assertEquals(bd("15000.00"), row.totals().balance());
        assertEquals(1, depositDao.ledger.size());
        var entry = auditDao.entries.get(0);
        assertEquals("DEPOSIT", entry.entityType());
        assertEquals("COLLECTED", entry.action());
        assertEquals("admin-test", entry.actorUsername());
        assertTrue(entry.newValue().contains("15000.00"));
    }

    @Test
    void collectionValidation() {
        assertThrows(ValidationException.class, () -> service.collect(1, bd("0"), TODAY, PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("-5"), TODAY, PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("10.001"), TODAY, PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("10000000000.00"), TODAY, PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, null, TODAY, PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("10"), null, PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("10"), TODAY.plusDays(1), PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("10"), LocalDate.of(1999, 12, 31), PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("10"), TODAY, null, null, null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("10"), TODAY, PaymentMethod.CASH, "r".repeat(61), null));
        assertThrows(ValidationException.class, () -> service.collect(1, bd("10"), TODAY, PaymentMethod.CASH, null, "n".repeat(501)));
        assertTrue(depositDao.ledger.isEmpty());
        assertDoesNotThrow(() -> service.collect(1, bd("0.01"), TODAY, PaymentMethod.CASH, null, null), "smallest valid amount");
        assertDoesNotThrow(() -> service.collect(1, bd("10"), LocalDate.of(2000, 1, 1), PaymentMethod.CASH, null, null), "earliest valid date");
    }

    @Test
    void collectionsCannotExceedTheAgreedDepositOrTargetEndedOrMissingAgreements() throws Exception {
        collect1("15000.00");
        assertDoesNotThrow(() -> collect1("5000.00"));
        assertEquals("DEP-03", assertThrows(BusinessRuleException.class, () -> collect1("0.01")).getRuleId());
        assertEquals("DEP-02", assertThrows(BusinessRuleException.class,
                () -> service.collect(2, bd("100"), TODAY, PaymentMethod.CASH, null, null)).getRuleId());
        assertThrows(EntityNotFoundException.class, () -> service.collect(99, bd("100"), TODAY, PaymentMethod.CASH, null, null));
        assertEquals(2, depositDao.ledger.size());
    }

    @Test
    void lastDayOfTheLeaseStillAcceptsACollection() throws Exception {
        agreement(3, LocalDate.of(2026, 7, 11), TODAY, "1000.00"); // ends today
        assertDoesNotThrow(() -> service.collect(3, bd("500"), TODAY, PaymentMethod.CASH, null, null));
        agreement(4, LocalDate.of(2026, 7, 10), TODAY.minusDays(1), "1000.00"); // ended yesterday
        assertEquals("DEP-02", assertThrows(BusinessRuleException.class, () -> service.collect(4, bd("500"), TODAY, PaymentMethod.CASH, null, null)).getRuleId());
    }

    // ---- deduct / refund

    @Test
    void deductionsNeedAReasonAndAnExistingDepositAndCannotExceedTheBalance() throws Exception {
        assertEquals("DEP-01", assertThrows(BusinessRuleException.class, () -> service.deduct(1, bd("10"), TODAY, "none held", null, null)).getRuleId());
        collect1("10000.00");
        assertThrows(ValidationException.class, () -> service.deduct(1, bd("10"), TODAY, "  ", null, null));
        assertThrows(ValidationException.class, () -> service.deduct(1, bd("10"), TODAY, "x".repeat(256), null, null));
        assertEquals("DEP-04", assertThrows(BusinessRuleException.class, () -> service.deduct(1, bd("10000.01"), TODAY, "too much", null, null)).getRuleId());
        assertEquals("DEP-09", assertThrows(BusinessRuleException.class, () -> service.deduct(1, bd("10"), LocalDate.of(2026, 8, 9), "early", null, null)).getRuleId());
        DepositRow row = service.deduct(1, bd("2500.50"), TODAY, "Broken window", "BILL-1", "photos kept");
        assertEquals(bd("7499.50"), row.totals().balance());
        assertEquals(bd("2500.50"), row.totals().deducted());
        assertDoesNotThrow(() -> service.deduct(1, bd("7499.50"), TODAY, "Rest of the balance", null, null), "can deduct the exact balance");
        assertEquals(DepositStatus.SETTLED, service.findPosition(1).orElseThrow().status());
        assertTrue(auditDao.actions().containsAll(List.of("COLLECTED", "DEDUCTED")));
        assertTrue(auditDao.entries.stream().filter(e -> e.action().equals("DEDUCTED")).allMatch(e -> e.newValue().contains("reason")));
    }

    @Test
    void refundsWaitForTheEndOfTheLeaseAndSupportPartialAndFullAmounts() throws Exception {
        collect1("20000.00");
        assertEquals("DEP-05", assertThrows(BusinessRuleException.class,
                () -> service.refund(1, bd("100"), TODAY, PaymentMethod.CASH, null, null)).getRuleId());
        seedEnded();
        assertEquals(bd("12000.00"), service.refund(2, bd("8000.00"), TODAY, PaymentMethod.BANK_TRANSFER, "NEFT", null).totals().balance());
        assertEquals("DEP-04", assertThrows(BusinessRuleException.class, () -> service.refund(2, bd("12000.01"), TODAY, PaymentMethod.CASH, null, null)).getRuleId());
        assertThrows(ValidationException.class, () -> service.refund(2, bd("100"), TODAY, null, null, null));
        DepositRow done = service.refund(2, bd("12000.00"), TODAY, PaymentMethod.CASH, null, null);
        assertEquals(bd("0.00"), done.totals().balance());
        assertEquals(DepositStatus.SETTLED, done.status());
        assertEquals("DEP-04", assertThrows(BusinessRuleException.class, () -> service.refund(2, bd("0.01"), TODAY, PaymentMethod.CASH, null, null)).getRuleId());
    }

    @Test
    void refundOnTheLastDayOfTheLeaseIsAllowed() throws Exception {
        agreement(5, LocalDate.of(2026, 7, 11), TODAY, "1000.00");
        service.collect(5, bd("1000"), LocalDate.of(2026, 7, 1), PaymentMethod.CASH, null, null);
        assertDoesNotThrow(() -> service.refund(5, bd("1000"), TODAY, PaymentMethod.CASH, null, null));
    }

    // ---- void

    @Test
    void voidingRulesProtectTheBalanceAndKeepTheEntry() throws Exception {
        collect1("10000.00");
        service.deduct(1, bd("4000.00"), TODAY, "Cleaning", null, null);
        DepositTransaction collection = depositDao.ledger.get(0), deduction = depositDao.ledger.get(1);
        assertEquals("DEP-06", assertThrows(BusinessRuleException.class, () -> service.voidTransaction(collection.txnId(), "oops")).getRuleId());
        assertEquals(bd("10000.00"), service.voidTransaction(deduction.txnId(), "Wrong tenant").totals().balance());
        assertEquals("DEP-08", assertThrows(BusinessRuleException.class, () -> service.voidTransaction(deduction.txnId(), "again")).getRuleId());
        assertThrows(ValidationException.class, () -> service.voidTransaction(collection.txnId(), ""));
        assertThrows(EntityNotFoundException.class, () -> service.voidTransaction(999, "missing"));
        assertEquals(2, depositDao.ledger.size(), "voided entries stay in the ledger");
        assertTrue(depositDao.ledger.get(1).isVoided());
        assertTrue(auditDao.actions().contains("TXN_VOIDED"));
    }

    @Test
    void renewalCarryOversCannotBeVoided() throws Exception {
        long id = depositDao.insertDeposit(1);
        depositDao.insertTransaction(new DepositTransaction(null, id, DepositType.TRANSFER_IN, bd("500"), TODAY, null, null, null, null, 2L, null, null, null));
        assertEquals("DEP-07", assertThrows(BusinessRuleException.class, () -> service.voidTransaction(depositDao.ledger.get(0).txnId(), "x")).getRuleId());
    }

    // ---- authorization

    @Test
    void viewerAndSignedOutCanNotChangeDepositsManagerCan() throws Exception {
        collect1("1000.00");
        long txnId = depositDao.ledger.get(0).txnId();
        for (Authorizer denied : new Authorizer[]{TestAuth.as(Role.VIEWER), TestAuth.signedOut()}) {
            DepositService s = serviceFor(denied);
            assertThrows(AuthorizationException.class, () -> s.collect(1, bd("10"), TODAY, PaymentMethod.CASH, null, null));
            assertThrows(AuthorizationException.class, () -> s.deduct(1, bd("10"), TODAY, "x", null, null));
            assertThrows(AuthorizationException.class, () -> s.refund(2, bd("10"), TODAY, PaymentMethod.CASH, null, null));
            assertThrows(AuthorizationException.class, () -> s.voidTransaction(txnId, "x"));
        }
        assertEquals(1, depositDao.ledger.size(), "denied calls wrote nothing");
        assertEquals(1, auditDao.entries.size());
        assertDoesNotThrow(() -> serviceFor(TestAuth.as(Role.MANAGER)).collect(1, bd("10"), TODAY, PaymentMethod.CASH, null, null));
        DepositService viewer = serviceFor(TestAuth.as(Role.VIEWER));
        assertTrue(viewer.findPosition(1).isPresent());
        assertEquals(2, viewer.transactions(1).size());
        assertDoesNotThrow(() -> viewer.report(LocalDate.of(2026, 1, 1), TODAY, null, null));
        DepositService out = serviceFor(TestAuth.signedOut());
        assertThrows(AuthorizationException.class, () -> out.findPosition(1));
        assertThrows(AuthorizationException.class, () -> out.report(LocalDate.of(2026, 1, 1), TODAY, null, null));
    }

    @Test
    void anAuditFailureAbortsTheChange() throws Exception {
        auditDao.failInserts = true;
        assertThrows(DataAccessException.class, () -> collect1("100.00"));
    }

    // ---- report

    @Test
    void reportSplitsLiabilityFromPeriodActivityAndGroupsRows() throws Exception {
        collect1("20000.00");
        service.deduct(1, bd("2000.00"), TODAY, "Damage", null, null);
        seedEnded();
        service.refund(2, bd("5000.00"), TODAY, PaymentMethod.CASH, null, null);
        agreement(3, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 2, 28), "8000.00"); // required, not collected

        DepositReport r = service.report(LocalDate.of(2026, 10, 1), TODAY, null, null);
        assertEquals(bd("33000.00"), r.liability(), "18000 + 15000 held now");
        assertEquals(0, r.collected().signum(), "no collections dated in October");
        assertEquals(bd("2000.00"), r.deducted());
        assertEquals(bd("5000.00"), r.refunded());
        assertEquals(3, r.rows().size());
        assertEquals(1, r.rows().stream().filter(x -> x.status() == DepositStatus.PENDING).count());
        var byProperty = DepositService.group(r.rows(), DepositService.Grouping.PROPERTY);
        assertEquals(3, byProperty.size());
        assertEquals(bd("33000.00"), byProperty.stream().map(g -> g.balance()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(3, DepositService.group(r.rows(), DepositService.Grouping.TENANT).size());
        assertEquals(3, DepositService.group(r.rows(), DepositService.Grouping.AGREEMENT).size());
    }

    @Test
    void reportRejectsBadRangesAndHandlesAnEmptyLedger() throws Exception {
        assertThrows(ValidationException.class, () -> service.report(null, TODAY, null, null));
        assertThrows(ValidationException.class, () -> service.report(TODAY, TODAY.minusDays(1), null, null));
        depositDao.agreements.clear();
        DepositReport r = service.report(TODAY, TODAY, null, null);
        assertEquals(0, r.liability().signum());
        assertTrue(r.rows().isEmpty() && r.activity().isEmpty());
        assertTrue(DepositService.group(List.of(), DepositService.Grouping.TENANT).isEmpty());
    }
}
