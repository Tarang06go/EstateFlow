package com.estateflow.service;

import com.estateflow.support.TestAuth;
import com.estateflow.dao.*;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.service.rules.RentScheduleGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.estateflow.service.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class AgreementServiceTest {
    AgreementDao agreementDao = mock(AgreementDao.class);
    RentChargeDao chargeDao = mock(RentChargeDao.class);
    PaymentDao paymentDao = mock(PaymentDao.class);
    PropertyDao propertyDao = mock(PropertyDao.class);
    TenantDao tenantDao = mock(TenantDao.class);
    PassThroughTransactionManager tx = new PassThroughTransactionManager();
    AgreementService service;

    @BeforeEach
    void setUp() {
        service = new AgreementService(agreementDao, chargeDao, paymentDao, propertyDao, tenantDao, tx, AuditService.noop(), CLOCK, 30, TestAuth.admin());
        when(propertyDao.findByIdForUpdate(1L)).thenReturn(Optional.of(property(1L, 1L, true)));
        when(tenantDao.findById(2L)).thenReturn(Optional.of(tenant(2L, true)));
        when(agreementDao.findOverlapping(anyLong(), any(), any())).thenReturn(List.of());
        when(agreementDao.insert(any(RentalAgreement.class))).thenAnswer(i -> {
            RentalAgreement a = i.getArgument(0);
            a.setAgreementId(7L);
            return a;
        });
    }

    private RentalAgreement create(LocalDate start, int months) throws Exception {
        return service.create(1L, 2L, start, months, new BigDecimal("15000.00"), new BigDecimal("30000.00"), null);
    }

    @Test
    @DisplayName("BR-10/11/14: end date, status and one charge per month are produced in one transaction")
    void create_validInput_insertsAgreementAndCharges() throws Exception {
        RentalAgreement a = create(LocalDate.of(2026, 1, 31), 3);
        assertEquals(LocalDate.of(2026, 4, 29), a.getEndDate()); // Jan 31 + 3 months clamps to Apr 30, minus one day (BR-11)
        assertEquals(AgreementStatus.ACTIVE, a.getStatus());
        assertEquals(1, tx.calls);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RentCharge>> captor = ArgumentCaptor.forClass(List.class);
        verify(chargeDao).insertAll(captor.capture());
        List<RentCharge> charges = captor.getValue();
        assertEquals(3, charges.size());
        assertEquals(LocalDate.of(2026, 2, 28), charges.get(1).getPeriodStart());
        assertEquals(LocalDate.of(2026, 3, 31), charges.get(2).getPeriodStart());
        assertEquals(charges.get(2).getPeriodStart(), charges.get(2).getDueDate());
    }

    @Test
    @DisplayName("BR-13: the property lock is the first data access inside the transaction")
    void create_locksPropertyBeforeAnyOtherDataAccess() throws Exception {
        create(LocalDate.of(2026, 11, 1), 12);
        InOrder order = inOrder(propertyDao, tenantDao, agreementDao, chargeDao);
        order.verify(propertyDao).findByIdForUpdate(1L);
        order.verify(tenantDao).findById(2L);
        order.verify(agreementDao).findOverlapping(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31));
        order.verify(agreementDao).insert(any(RentalAgreement.class));
        order.verify(chargeDao).insertAll(any());
    }

    @Test
    @DisplayName("BR-13: overlapping agreement is rejected and nothing is inserted")
    void create_overlap_throwsBr13() {
        RentalAgreement existing = new RentalAgreement();
        existing.setStartDate(LocalDate.of(2026, 6, 1));
        existing.setEndDate(LocalDate.of(2027, 5, 31));
        when(agreementDao.findOverlapping(anyLong(), any(), any())).thenReturn(List.of(existing));
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> create(LocalDate.of(2026, 11, 1), 12));
        assertEquals("BR-13", e.getRuleId());
        verify(agreementDao, never()).insert(any());
        verify(chargeDao, never()).insertAll(any());
    }

    @Test
    @DisplayName("BR-05: inactive property or tenant cannot be used in a new agreement")
    void create_inactiveParties_throwsBr05() {
        when(propertyDao.findByIdForUpdate(1L)).thenReturn(Optional.of(property(1L, 1L, false)));
        assertEquals("BR-05", assertThrows(BusinessRuleException.class, () -> create(TODAY, 12)).getRuleId());
        when(propertyDao.findByIdForUpdate(1L)).thenReturn(Optional.of(property(1L, 1L, true)));
        when(tenantDao.findById(2L)).thenReturn(Optional.of(tenant(2L, false)));
        assertEquals("BR-05", assertThrows(BusinessRuleException.class, () -> create(TODAY, 12)).getRuleId());
    }

    @Test
    @DisplayName("BR-10: duration 1-120, rent > 0, deposit >= 0 are validated before any transaction")
    void create_invalidTerms_throwsValidationWithoutTransaction() {
        assertThrows(ValidationException.class, () -> create(TODAY, 0));
        assertThrows(ValidationException.class, () -> create(TODAY, 121));
        assertThrows(ValidationException.class, () ->
                service.create(1L, 2L, TODAY, 12, BigDecimal.ZERO, BigDecimal.ZERO, null));
        assertThrows(ValidationException.class, () ->
                service.create(1L, 2L, TODAY, 12, new BigDecimal("100"), new BigDecimal("-1"), null));
        assertThrows(ValidationException.class, () ->
                service.create(1L, 2L, null, 12, new BigDecimal("100"), BigDecimal.ZERO, null));
        assertEquals(0, tx.calls);
    }

    @Test
    @DisplayName("BR-10: a start date in the past is allowed")
    void create_pastStartDate_allowed() throws Exception {
        assertNotNull(create(LocalDate.of(2025, 1, 1), 24));
    }

    // ---- termination
    private RentalAgreement active() {
        RentalAgreement a = new RentalAgreement();
        a.setAgreementId(7L);
        a.setStartDate(LocalDate.of(2026, 1, 1));
        a.setDurationMonths(12);
        a.setEndDate(LocalDate.of(2026, 12, 31));
        a.setStatus(AgreementStatus.ACTIVE);
        return a;
    }

    @Test
    @DisplayName("BR-15: termination sets status/date, then cancels charges after the termination date")
    void terminate_valid_updatesAndCancelsChargesInTransaction() throws Exception {
        RentalAgreement a = active();
        when(agreementDao.findById(7L)).thenReturn(Optional.of(a));
        when(paymentDao.existsValidForChargesAfter(7L, LocalDate.of(2026, 11, 15))).thenReturn(false);
        when(agreementDao.update(a)).thenReturn(1);
        RentalAgreement result = service.terminate(7L, LocalDate.of(2026, 11, 15));
        assertEquals(AgreementStatus.TERMINATED, result.getStatus());
        assertEquals(LocalDate.of(2026, 11, 15), result.getEffectiveEndDate());
        verify(chargeDao).cancelAfter(eq7(), eq(LocalDate.of(2026, 11, 15)), any());
        assertEquals(1, tx.calls);
    }

    private static Long eq7() {
        return org.mockito.ArgumentMatchers.eq(7L);
    }

    @Test
    @DisplayName("BR-15: rejected when valid payments exist after the termination date")
    void terminate_validPaymentsAfter_throwsBr15() {
        when(agreementDao.findById(7L)).thenReturn(Optional.of(active()));
        when(paymentDao.existsValidForChargesAfter(anyLong(), any())).thenReturn(true);
        assertEquals("BR-15", assertThrows(BusinessRuleException.class,
                () -> service.terminate(7L, LocalDate.of(2026, 3, 1))).getRuleId());
        verify(chargeDao, never()).cancelAfter(anyLong(), any(), any());
    }

    @Test
    @DisplayName("BR-15: date must satisfy start <= termination < end")
    void terminate_dateOutsideRange_throwsBr15() {
        when(agreementDao.findById(7L)).thenReturn(Optional.of(active()));
        assertThrows(BusinessRuleException.class, () -> service.terminate(7L, LocalDate.of(2025, 12, 31)));
        assertThrows(BusinessRuleException.class, () -> service.terminate(7L, LocalDate.of(2026, 12, 31)));
        assertDoesNotThrow(() -> {
            when(agreementDao.update(any())).thenReturn(1);
            service.terminate(7L, LocalDate.of(2026, 1, 1));
        });
    }

    @Test
    @DisplayName("BR-15: expired or already-terminated agreements cannot be terminated")
    void terminate_expiredOrTerminated_throwsBr15() {
        RentalAgreement expired = active();
        expired.setStartDate(LocalDate.of(2025, 1, 1));
        expired.setEndDate(LocalDate.of(2025, 12, 31));
        when(agreementDao.findById(7L)).thenReturn(Optional.of(expired));
        assertThrows(BusinessRuleException.class, () -> service.terminate(7L, LocalDate.of(2025, 6, 1)));
        RentalAgreement done = active();
        done.setStatus(AgreementStatus.TERMINATED);
        done.setTerminationDate(LocalDate.of(2026, 5, 1));
        when(agreementDao.findById(7L)).thenReturn(Optional.of(done));
        assertThrows(BusinessRuleException.class, () -> service.terminate(7L, LocalDate.of(2026, 6, 1)));
    }

    @Test
    @DisplayName("BR-16: only notes can change; too-long notes are rejected")
    void updateNotes_changesOnlyNotes() throws Exception {
        RentalAgreement a = active();
        a.setMonthlyRent(new BigDecimal("100.00"));
        when(agreementDao.findById(7L)).thenReturn(Optional.of(a));
        when(agreementDao.update(a)).thenReturn(1);
        service.updateNotes(7L, " paid deposit ");
        assertEquals("paid deposit", a.getNotes());
        assertEquals(new BigDecimal("100.00"), a.getMonthlyRent());
        assertThrows(ValidationException.class, () -> service.updateNotes(7L, "x".repeat(501)));
    }

    @Test
    @DisplayName("BR-27: ending-soon window uses the configured warning days from today")
    void findEndingSoon_usesWarningWindowFromClock() {
        service.findEndingSoon();
        verify(agreementDao).findEndingBetween(TODAY, TODAY.plusDays(30));
    }

    @Test
    @DisplayName("BR-12/17 helper: schedule generator and effective status agree with the model")
    void generator_endDateMatchesRule() {
        assertEquals(LocalDate.of(2026, 12, 31), RentScheduleGenerator.endDate(LocalDate.of(2026, 1, 1), 12));
        assertEquals(LocalDate.of(2027, 2, 27), RentScheduleGenerator.endDate(LocalDate.of(2026, 2, 28), 12));
    }
}
