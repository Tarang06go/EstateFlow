package com.estateflow.service;

import com.estateflow.dao.*;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.model.enums.*;
import com.estateflow.security.Authorizer;
import com.estateflow.support.FakeAuditDao;
import com.estateflow.support.FakeDepositDao;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LeaseRenewalServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private final AgreementDao agreementDao = mock(AgreementDao.class);
    private final LeaseDao leaseDao = mock(LeaseDao.class);
    private final RentChargeDao chargeDao = mock(RentChargeDao.class);
    private final PropertyDao propertyDao = mock(PropertyDao.class);
    private final TenantDao tenantDao = mock(TenantDao.class);
    private FakeDepositDao depositDao;
    private FakeAuditDao auditDao;
    private LeaseRenewalService service;
    private Property property;
    private Tenant tenant;

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private LeaseRenewalService serviceFor(Authorizer auth) {
        return new LeaseRenewalService(agreementDao, leaseDao, chargeDao, propertyDao, tenantDao, depositDao, new PassThroughTransactionManager(),
                new AuditService(auditDao, CLOCK, auth), CLOCK, auth);
    }

    private RentalAgreement lease(long id, LocalDate start, int months, LocalDate end) {
        RentalAgreement a = new RentalAgreement();
        a.setAgreementId(id);
        a.setPropertyId(7L);
        a.setTenantId(8L);
        a.setStartDate(start);
        a.setDurationMonths(months);
        a.setEndDate(end);
        a.setMonthlyRent(bd("10000.00"));
        a.setSecurityDeposit(bd("20000.00"));
        a.setStatus(AgreementStatus.ACTIVE);
        a.setNotes("n");
        when(agreementDao.findById(id)).thenReturn(Optional.of(a));
        depositDao.agreements.put(id, a);
        return a;
    }

    @BeforeEach
    void setUp() {
        depositDao = new FakeDepositDao();
        auditDao = new FakeAuditDao();
        service = serviceFor(TestAuth.admin());
        property = new Property();
        property.setPropertyId(7L);
        property.setName("Flat 7");
        property.setActive(true);
        tenant = new Tenant();
        tenant.setFullName("Asha");
        tenant.setActive(true);
        when(propertyDao.findByIdForUpdate(7L)).thenReturn(Optional.of(property));
        when(tenantDao.findById(8L)).thenReturn(Optional.of(tenant));
        when(leaseDao.supportsRenewal()).thenReturn(true);
        when(leaseDao.findSuccessor(anyLong())).thenReturn(Optional.empty());
        when(agreementDao.findOverlapping(anyLong(), any(), any())).thenReturn(List.of());
        doAnswer(i -> {
            RentalAgreement a = i.getArgument(0);
            a.setAgreementId(50L);
            return a;
        }).when(agreementDao).insert(any());
        when(leaseDao.extendTerm(anyLong(), anyInt(), any())).thenReturn(1);
    }

    // ---- renewal

    @Test
    void renewalStartsTheDayAfterTheOldLeaseEndsAndLinksAndAudits() throws Exception {
        lease(1, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        RentalAgreement next = service.renew(1, 6, bd("11000.00"), bd("22000.00"), false, "Annual review");
        assertEquals(LocalDate.of(2026, 11, 15), next.getStartDate());
        assertEquals(LocalDate.of(2027, 5, 14), next.getEndDate());
        assertEquals(bd("11000.00"), next.getMonthlyRent());
        assertEquals(bd("22000.00"), next.getSecurityDeposit());
        assertEquals(AgreementStatus.ACTIVE, next.getStatus());
        verify(leaseDao).setRenewedFrom(50L, 1L);
        ArgumentCaptor<List<RentCharge>> charges = ArgumentCaptor.forClass(List.class);
        verify(chargeDao).insertAll(charges.capture());
        assertEquals(6, charges.getValue().size());
        ArgumentCaptor<LeaseChange> change = ArgumentCaptor.forClass(LeaseChange.class);
        verify(leaseDao).insertChange(change.capture());
        LeaseChange c = change.getValue();
        assertEquals("RENEWED", c.changeType());
        assertEquals(1L, c.relatedAgreementId());
        assertEquals(LocalDate.of(2026, 11, 14), c.oldEndDate());
        assertEquals(bd("10000.00"), c.oldMonthlyRent());
        assertEquals(bd("11000.00"), c.newMonthlyRent());
        assertEquals("admin-test", c.actorUsername());
        assertEquals(1L, c.actorUserId());
        var audit = auditDao.entries.get(0);
        assertEquals("AGREEMENT", audit.entityType());
        assertEquals("RENEWED", audit.action());
        assertEquals("admin-test", audit.actorUsername());
        verify(agreementDao, never()).update(any());
    }

    @Test
    void ineligibleAgreementsAreRefusedAndNothingIsWritten() throws Exception {
        RentalAgreement terminated = lease(2, LocalDate.of(2026, 8, 15), 6, LocalDate.of(2027, 2, 14));
        terminated.setStatus(AgreementStatus.TERMINATED);
        terminated.setTerminationDate(LocalDate.of(2026, 10, 1));
        assertEquals("LR-01", assertThrows(BusinessRuleException.class, () -> service.renew(2, 6, bd("1"), null, false, null)).getRuleId());
        assertEquals("LR-01", assertThrows(BusinessRuleException.class, () -> service.extend(2, 1, null)).getRuleId());

        lease(3, LocalDate.of(2026, 8, 15), 6, LocalDate.of(2027, 2, 14));
        when(leaseDao.findSuccessor(3L)).thenReturn(Optional.of(77L));
        BusinessRuleException renewed = assertThrows(BusinessRuleException.class, () -> service.renew(3, 6, bd("1"), null, false, null));
        assertTrue(renewed.getMessage().contains("77"));

        lease(4, LocalDate.of(2026, 12, 1), 6, LocalDate.of(2027, 5, 31));
        assertTrue(service.whyNotRenewable(4).orElseThrow().contains("not started"));
        assertThrows(BusinessRuleException.class, () -> service.renew(4, 6, bd("1"), null, false, null));

        verify(agreementDao, never()).insert(any());
        verify(chargeDao, never()).insertAll(any());
        verify(leaseDao, never()).insertChange(any());
        assertTrue(auditDao.entries.isEmpty());
    }

    @Test
    void theRenewalGracePeriodBoundaryIsThirtyDays() throws Exception {
        lease(5, LocalDate.of(2026, 6, 11), 3, TODAY.minusDays(30));      // ended exactly 30 days ago
        lease(6, LocalDate.of(2026, 6, 10), 3, TODAY.minusDays(31));      // ended 31 days ago
        lease(7, LocalDate.of(2026, 7, 11), 3, TODAY);                    // ends today
        assertTrue(service.whyNotRenewable(5).isEmpty());
        assertTrue(service.whyNotRenewable(6).orElseThrow().contains("more than 30 days"));
        assertTrue(service.whyNotRenewable(7).isEmpty());
        assertTrue(service.whyNotExtendable(7).isEmpty(), "can extend on the last day");
        assertTrue(service.whyNotExtendable(5).orElseThrow().contains("Renew"), "an ended lease is renewed, not extended");
    }

    @Test
    void renewalRejectsOverlapsInactivePartiesAndBadInput() throws Exception {
        lease(1, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        RentalAgreement other = new RentalAgreement();
        other.setStartDate(LocalDate.of(2026, 11, 20));
        other.setEndDate(LocalDate.of(2027, 5, 19));
        when(agreementDao.findOverlapping(eq(7L), eq(LocalDate.of(2026, 11, 15)), eq(LocalDate.of(2027, 5, 14)))).thenReturn(List.of(other));
        assertEquals("BR-13", assertThrows(BusinessRuleException.class, () -> service.renew(1, 6, bd("1000"), null, false, null)).getRuleId());
        when(agreementDao.findOverlapping(anyLong(), any(), any())).thenReturn(List.of());

        property.setActive(false);
        assertEquals("BR-05", assertThrows(BusinessRuleException.class, () -> service.renew(1, 6, bd("1000"), null, false, null)).getRuleId());
        property.setActive(true);
        tenant.setActive(false);
        assertEquals("BR-05", assertThrows(BusinessRuleException.class, () -> service.renew(1, 6, bd("1000"), null, false, null)).getRuleId());
        tenant.setActive(true);

        for (int months : new int[]{0, -1, 121}) {
            assertThrows(ValidationException.class, () -> service.renew(1, months, bd("1000"), null, false, null));
        }
        assertThrows(ValidationException.class, () -> service.renew(1, 6, bd("0"), null, false, null));
        assertThrows(ValidationException.class, () -> service.renew(1, 6, bd("-1"), null, false, null));
        assertThrows(ValidationException.class, () -> service.renew(1, 6, null, null, false, null));
        assertThrows(ValidationException.class, () -> service.renew(1, 6, bd("1000.005"), null, false, null));
        assertThrows(ValidationException.class, () -> service.renew(1, 6, bd("1000"), bd("-1"), false, null));
        assertThrows(ValidationException.class, () -> service.renew(1, 6, bd("1000"), null, false, "n".repeat(256)));
        assertDoesNotThrow(() -> service.renew(1, 120, bd("1000"), null, false, null), "120 months is the limit");
        assertDoesNotThrow(() -> service.renew(1, 1, bd("0.01"), bd("0"), false, null), "boundary values are accepted");
    }

    @Test
    void duplicateRenewalIsStoppedByTheUniqueLink() throws Exception {
        lease(1, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        when(leaseDao.setRenewedFrom(anyLong(), anyLong())).thenThrow(new com.estateflow.exception.DuplicateKeyException("dup",
                new java.sql.SQLException("dup", "23000", 1062), "uq_agreement_renewed_from"));
        assertEquals("LR-02", assertThrows(BusinessRuleException.class, () -> service.renew(1, 6, bd("1000"), null, false, null)).getRuleId());
    }

    // ---- deposit carry-over

    private long seedDeposit(long agreementId, String amount) {
        long id = depositDao.insertDeposit(agreementId);
        depositDao.insertTransaction(new DepositTransaction(null, id, DepositType.COLLECTION, bd(amount), LocalDate.of(2026, 8, 1), PaymentMethod.CASH,
                null, null, null, null, null, null, null));
        return id;
    }

    @Test
    void carryOverMovesTheBalanceByLedgerTransferWithoutCreatingOrLosingMoney() throws Exception {
        lease(1, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        long oldDeposit = seedDeposit(1, "20000.00");
        depositDao.insertTransaction(new DepositTransaction(null, oldDeposit, DepositType.DEDUCTION, bd("1500.00"), TODAY, null, null, "Damage", null,
                null, null, null, null));
        service.renew(1, 6, bd("11000.00"), bd("20000.00"), true, null);
        assertEquals(bd("0.00"), depositDao.totals(oldDeposit).balance());
        long newDeposit = depositDao.findDepositId(50).orElseThrow();
        assertEquals(bd("18500.00"), depositDao.totals(newDeposit).balance());
        assertEquals(bd("18500.00"), depositDao.totals(newDeposit).transferredIn());
        assertEquals(bd("18500.00"), depositDao.totals(oldDeposit).transferredOut());
        assertTrue(auditDao.actions().containsAll(List.of("TRANSFERRED_OUT", "TRANSFERRED_IN", "RENEWED")));
    }

    @Test
    void withoutCarryOverTheDepositStaysAndAnOversizedCarryIsRefused() throws Exception {
        lease(1, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        long oldDeposit = seedDeposit(1, "20000.00");
        assertEquals("LR-05", assertThrows(BusinessRuleException.class, () -> service.renew(1, 6, bd("1000"), bd("5000"), true, null)).getRuleId());
        verify(agreementDao, never()).insert(any());
        service.renew(1, 6, bd("1000"), bd("5000"), false, null);
        assertEquals(bd("20000.00"), depositDao.totals(oldDeposit).balance());
        assertTrue(depositDao.findDepositId(50).isEmpty());
    }

    // ---- extension

    @Test
    void extensionAddsOnlyTheNewRentPeriodsAndKeepsTheRent() throws Exception {
        RentalAgreement a = lease(1, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        service.extend(1, 3, "More time");
        verify(leaseDao).extendTerm(1L, 6, LocalDate.of(2027, 2, 14));
        ArgumentCaptor<List<RentCharge>> charges = ArgumentCaptor.forClass(List.class);
        verify(chargeDao).insertAll(charges.capture());
        assertEquals(3, charges.getValue().size(), "only the added months");
        assertTrue(charges.getValue().stream().allMatch(c -> c.getAmountDue().compareTo(a.getMonthlyRent()) == 0));
        ArgumentCaptor<LeaseChange> change = ArgumentCaptor.forClass(LeaseChange.class);
        verify(leaseDao).insertChange(change.capture());
        assertEquals("EXTENDED", change.getValue().changeType());
        assertEquals(LocalDate.of(2026, 11, 14), change.getValue().oldEndDate());
        assertEquals(LocalDate.of(2027, 2, 14), change.getValue().newEndDate());
        assertEquals(change.getValue().oldMonthlyRent(), change.getValue().newMonthlyRent());
        assertEquals("admin-test", change.getValue().actorUsername());
        assertEquals("EXTENDED", auditDao.entries.get(0).action());
        assertTrue(auditDao.entries.get(0).oldValue().contains("2026-11-14"));
    }

    @Test
    void extensionLimitsAndOverlapsAreEnforced() throws Exception {
        lease(1, LocalDate.of(2026, 8, 15), 118, LocalDate.of(2036, 6, 14));
        assertEquals("LR-07", assertThrows(BusinessRuleException.class, () -> service.extend(1, 3, null)).getRuleId());
        assertThrows(ValidationException.class, () -> service.extend(1, 0, null));
        assertThrows(ValidationException.class, () -> service.extend(1, 121, null));
        assertDoesNotThrow(() -> service.extend(1, 2, null), "exactly 120 months");

        lease(2, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        when(agreementDao.findOverlapping(eq(7L), eq(LocalDate.of(2026, 11, 15)), eq(LocalDate.of(2027, 1, 14)))).thenReturn(List.of(new RentalAgreement()));
        assertEquals("BR-13", assertThrows(BusinessRuleException.class, () -> service.extend(2, 2, null)).getRuleId());
        verify(leaseDao, never()).extendTerm(eq(2L), anyInt(), any());
    }

    // ---- authorization

    @Test
    void viewerAndSignedOutCannotRenewOrExtendManagerCan() throws Exception {
        lease(1, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        for (Authorizer denied : new Authorizer[]{TestAuth.as(Role.VIEWER), TestAuth.signedOut()}) {
            LeaseRenewalService s = serviceFor(denied);
            assertThrows(AuthorizationException.class, () -> s.renew(1, 6, bd("1000"), null, false, null));
            assertThrows(AuthorizationException.class, () -> s.extend(1, 1, null));
        }
        verify(agreementDao, never()).insert(any());
        verify(leaseDao, never()).extendTerm(anyLong(), anyInt(), any());
        assertDoesNotThrow(() -> serviceFor(TestAuth.as(Role.MANAGER)).extend(1, 1, null));
        LeaseRenewalService viewer = serviceFor(TestAuth.as(Role.VIEWER));
        assertDoesNotThrow(() -> viewer.whyNotRenewable(1));
        assertDoesNotThrow(() -> viewer.history(1));
        assertDoesNotThrow(() -> viewer.needingAttention());
        LeaseRenewalService out = serviceFor(TestAuth.signedOut());
        assertThrows(AuthorizationException.class, () -> out.history(1));
        assertThrows(AuthorizationException.class, () -> out.endingWithin(30));
    }

    @Test
    void anAuditFailureAbortsARenewal() throws Exception {
        lease(1, LocalDate.of(2026, 8, 15), 3, LocalDate.of(2026, 11, 14));
        auditDao.failInserts = true;
        assertThrows(DataAccessException.class, () -> service.renew(1, 6, bd("1000"), null, false, null));
    }

    @Test
    void missingMigrationGivesASetupMessage() {
        when(leaseDao.supportsRenewal()).thenReturn(false);
        assertTrue(assertThrows(ConfigurationException.class, () -> service.ensureReady()).getMessage().contains("v1.5_deposits_leases.sql"));
        assertThrows(ConfigurationException.class, () -> service.extend(1, 1, null));
        assertTrue(service.history(1).isEmpty());
    }

    // ---- expiry windows

    @Test
    void expiryWindowUsesInclusiveDatesAndIsValidated() throws Exception {
        when(agreementDao.findEndingBetween(any(), any())).thenReturn(List.<ExpiringLeaseRow>of());
        service.endingWithin(30);
        verify(agreementDao).findEndingBetween(TODAY, TODAY.plusDays(30));
        service.endingWithin(90);
        verify(agreementDao).findEndingBetween(TODAY, TODAY.plusDays(90));
        service.endingWithin(1);
        service.endingWithin(365);
        assertThrows(ValidationException.class, () -> service.endingWithin(0));
        assertThrows(ValidationException.class, () -> service.endingWithin(-5));
        assertThrows(ValidationException.class, () -> service.endingWithin(366));
    }

    @Test
    void configuredExpiryWindowSupportsTheFullApplicationConfigRange() throws Exception {
        when(agreementDao.findEndingBetween(any(), any())).thenReturn(List.<ExpiringLeaseRow>of());
        service.endingWithinWarningWindow(0);
        verify(agreementDao).findEndingBetween(TODAY, TODAY);
        service.endingWithinWarningWindow(3650);
        verify(agreementDao).findEndingBetween(TODAY, TODAY.plusDays(3650));
        assertThrows(ValidationException.class, () -> service.endingWithinWarningWindow(3651));
    }
}
