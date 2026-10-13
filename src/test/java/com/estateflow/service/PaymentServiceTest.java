package com.estateflow.service;

import com.estateflow.dao.*;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.model.enums.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.estateflow.service.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    PaymentDao paymentDao = mock(PaymentDao.class);
    RentChargeDao chargeDao = mock(RentChargeDao.class);
    ReportingDao reportingDao = mock(ReportingDao.class);
    PassThroughTransactionManager tx = new PassThroughTransactionManager();
    PaymentService service;
    static final LocalDate DUE = LocalDate.of(2026, 10, 1);

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentDao, chargeDao, reportingDao, tx, AuditService.noop(), CLOCK);
        when(paymentDao.insert(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setPaymentId(11L);
            return p;
        });
    }

    private Payment pay(String amount) throws Exception {
        return service.recordPayment(5L, new BigDecimal(amount), TODAY, PaymentMethod.UPI, " ref1 ", null);
    }

    @Test
    @DisplayName("BR-20/21/23: partial payment within the balance is recorded; charge lock comes first")
    void recordPayment_withinBalance_lockedFirstThenInserted() throws Exception {
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(charge(5L, DUE, "15000.00", "5000.00")));
        Payment p = pay("10000.00");
        assertEquals("ref1", p.getReferenceNo());
        InOrder order = inOrder(chargeDao, paymentDao);
        order.verify(chargeDao).findByIdForUpdate(5L);
        order.verify(paymentDao).insert(any(Payment.class));
        assertEquals(1, tx.calls);
    }

    @Test
    @DisplayName("BR-21: overpayment is rejected with the amounts in the message")
    void recordPayment_overBalance_throwsBr21() {
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(charge(5L, DUE, "15000.00", "14000.00")));
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> pay("1000.01"));
        assertEquals("BR-21", e.getRuleId());
        assertTrue(e.getMessage().contains("exceeds the remaining balance"));
        verify(paymentDao, never()).insert(any());
    }

    @Test
    @DisplayName("BR-21: paying exactly the balance is allowed")
    void recordPayment_exactBalance_allowed() throws Exception {
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(charge(5L, DUE, "15000.00", "14000.00")));
        assertNotNull(pay("1000.00"));
    }

    @Test
    @DisplayName("BR-20: advance payment against a future charge is rejected")
    void recordPayment_futureCharge_throwsBr20() {
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(charge(5L, TODAY.plusDays(1), "15000.00", "0")));
        assertEquals("BR-20", assertThrows(BusinessRuleException.class, () -> pay("100")).getRuleId());
    }

    @Test
    @DisplayName("BR-20: a charge due today is payable")
    void recordPayment_dueToday_allowed() throws Exception {
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(charge(5L, TODAY, "15000.00", "0")));
        assertNotNull(pay("100"));
    }

    @Test
    @DisplayName("BR-20: a cancelled charge cannot receive payments")
    void recordPayment_cancelledCharge_throwsBr20() {
        RentCharge c = charge(5L, DUE, "15000.00", "0");
        c.setCancelledAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(c));
        assertEquals("BR-20", assertThrows(BusinessRuleException.class, () -> pay("100")).getRuleId());
    }

    @Test
    @DisplayName("BR-24: arrears on an old (expired/terminated agreement) charge can be paid")
    void recordPayment_oldCharge_allowed() throws Exception {
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(charge(5L, LocalDate.of(2024, 1, 1), "100.00", "0")));
        assertNotNull(pay("100.00"));
    }

    @Test
    @DisplayName("BR-21/22: amount and date validation happens before any transaction")
    void recordPayment_invalidInput_throwsValidationWithoutTransaction() {
        assertThrows(ValidationException.class, () -> pay("0"));
        assertThrows(ValidationException.class, () -> pay("-5"));
        assertThrows(ValidationException.class, () -> pay("10.001"));
        assertThrows(ValidationException.class, () ->
                service.recordPayment(5L, BigDecimal.TEN, TODAY.plusDays(1), PaymentMethod.CASH, null, null));
        assertThrows(ValidationException.class, () ->
                service.recordPayment(5L, BigDecimal.TEN, TODAY, null, null, null));
        assertThrows(ValidationException.class, () ->
                service.recordPayment(null, BigDecimal.TEN, TODAY, PaymentMethod.CASH, null, null));
        assertEquals(0, tx.calls);
    }

    @Test
    void recordPayment_missingCharge_throwsNotFound() {
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> pay("10"));
    }

    // ---- BR-25
    private Payment stored(boolean voided) {
        Payment p = new Payment(5L, new BigDecimal("100.00"), TODAY, PaymentMethod.CASH, null, null);
        p.setPaymentId(11L);
        if (voided) {
            p.setVoidedAt(LocalDateTime.of(2026, 10, 9, 0, 0));
            p.setVoidReason("typo");
        }
        return p;
    }

    @Test
    @DisplayName("BR-25: void needs a 1-255 character reason")
    void voidPayment_badReason_throwsValidation() {
        assertThrows(ValidationException.class, () -> service.voidPayment(11L, "  "));
        assertThrows(ValidationException.class, () -> service.voidPayment(11L, null));
        assertThrows(ValidationException.class, () -> service.voidPayment(11L, "x".repeat(256)));
        verify(paymentDao, never()).markVoided(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("BR-25: void stores the trimmed reason and a UTC timestamp from the clock")
    void voidPayment_valid_marksVoided() throws Exception {
        when(paymentDao.findById(11L)).thenReturn(Optional.of(stored(false)));
        when(paymentDao.markVoided(anyLong(), anyString(), any())).thenReturn(1);
        service.voidPayment(11L, "  wrong amount ");
        verify(paymentDao).markVoided(11L, "wrong amount", LocalDateTime.of(2026, 10, 10, 8, 0));
    }

    @Test
    @DisplayName("BR-25: a payment cannot be voided twice (already void, or lost the race)")
    void voidPayment_alreadyVoid_throwsBr25() {
        when(paymentDao.findById(11L)).thenReturn(Optional.of(stored(true)));
        assertEquals("BR-25", assertThrows(BusinessRuleException.class, () -> service.voidPayment(11L, "again")).getRuleId());
        when(paymentDao.findById(11L)).thenReturn(Optional.of(stored(false)));
        when(paymentDao.markVoided(anyLong(), anyString(), any())).thenReturn(0);
        assertEquals("BR-25", assertThrows(BusinessRuleException.class, () -> service.voidPayment(11L, "race")).getRuleId());
    }

    @Test
    @DisplayName("Section 5.4: outstanding balance counts only payable (live, due) charges")
    void outstandingBalance_excludesFutureAndCancelled() {
        RentCharge cancelled = charge(3L, DUE, "500.00", "0");
        cancelled.setCancelledAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        when(chargeDao.findByAgreement(7L)).thenReturn(List.of(
                charge(1L, DUE, "1000.00", "400.00"),
                charge(2L, TODAY, "1000.00", "0"),
                charge(4L, TODAY.plusDays(1), "1000.00", "0"),
                cancelled));
        assertEquals(new BigDecimal("1600.00"), service.outstandingBalance(7L));
    }
}
