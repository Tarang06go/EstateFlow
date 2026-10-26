package com.estateflow.service;

import com.estateflow.support.TestAuth;
import com.estateflow.dao.*;
import com.estateflow.exception.BusinessRuleException;
import com.estateflow.model.*;
import com.estateflow.model.enums.AgreementStatus;
import com.estateflow.model.enums.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static com.estateflow.service.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditServiceTest {
    AuditDao auditDao = mock(AuditDao.class);

    private AuditService enabled() {
        when(auditDao.isAvailable()).thenReturn(true);
        return new AuditService(auditDao, CLOCK, TestAuth.admin());
    }

    @Test
    void noop_isDisabledAndRecordsNothing() {
        AuditService noop = AuditService.noop();
        assertFalse(noop.isEnabled());
        noop.record("X", 1, "A", null, null);
        assertTrue(noop.find(null, null, 10).isEmpty());
    }

    @Test
    @DisplayName("v1.1 migration not applied: auditing is skipped, the table check runs once")
    void unavailableTable_disablesAuditing_checkedOnce() {
        when(auditDao.isAvailable()).thenReturn(false);
        AuditService service = new AuditService(auditDao, CLOCK, TestAuth.admin());
        service.record("X", 1, "A", null, null);
        service.record("X", 2, "A", null, null);
        verify(auditDao, times(1)).isAvailable();
        verify(auditDao, never()).insert(any(), anyLong(), any(), any(), any(), any(), any(), any());
        assertTrue(service.find(null, null, 5).isEmpty());
    }

    @Test
    @DisplayName("Entries carry JSON text and a UTC timestamp taken from the clock")
    void record_writesJsonAndUtcTimestamp() {
        AuditService service = enabled();
        service.record("PAYMENT", 9, "VOIDED", AuditService.details("status", "VALID", "amount", new BigDecimal("10.00")),
                AuditService.details("reason", "say \"typo\"\nline2", "none", null, "ok", true));
        verify(auditDao).insert("PAYMENT", 9L, "VOIDED", "{\"status\":\"VALID\",\"amount\":10.00}",
                "{\"reason\":\"say \\\"typo\\\"\\nline2\",\"none\":null,\"ok\":true}", LocalDateTime.of(2026, 10, 10, 8, 0), 1L, "admin-test");
    }

    @Test
    void toJson_nullMapIsNull_controlCharactersEscaped() {
        assertNull(AuditService.toJson(null));
        assertEquals("{\"a\":\"\\u0001\\\\\"}", AuditService.toJson(Map.of("a", "\u0001\\")));
    }

    // ---- wiring: the audit entry is written inside the operation's transaction
    @Test
    @DisplayName("Agreement creation and termination write CREATED / TERMINATED audit entries")
    void agreementOperations_areAudited() throws Exception {
        AuditService audit = enabled();
        AgreementDao agreementDao = mock(AgreementDao.class);
        PropertyDao propertyDao = mock(PropertyDao.class);
        TenantDao tenantDao = mock(TenantDao.class);
        PaymentDao paymentDao = mock(PaymentDao.class);
        when(propertyDao.findByIdForUpdate(1L)).thenReturn(Optional.of(property(1L, 1L, true)));
        when(tenantDao.findById(2L)).thenReturn(Optional.of(tenant(2L, true)));
        when(agreementDao.findOverlapping(anyLong(), any(), any())).thenReturn(java.util.List.of());
        when(agreementDao.insert(any(RentalAgreement.class))).thenAnswer(i -> {
            RentalAgreement a = i.getArgument(0);
            a.setAgreementId(7L);
            return a;
        });
        AgreementService service = new AgreementService(agreementDao, mock(RentChargeDao.class), paymentDao, propertyDao,
                tenantDao, new PassThroughTransactionManager(), audit, CLOCK, 30, TestAuth.admin());
        service.create(1L, 2L, LocalDate.of(2026, 11, 1), 12, new BigDecimal("1000.00"), null, null);
        ArgumentCaptor<String> newValue = ArgumentCaptor.forClass(String.class);
        verify(auditDao).insert(eq("AGREEMENT"), eq(7L), eq("CREATED"), isNull(), newValue.capture(), any(), any(), any());
        assertTrue(newValue.getValue().contains("\"endDate\":\"2027-10-31\""));

        RentalAgreement stored = new RentalAgreement();
        stored.setAgreementId(7L);
        stored.setStartDate(LocalDate.of(2026, 11, 1));
        stored.setEndDate(LocalDate.of(2027, 10, 31));
        stored.setStatus(AgreementStatus.ACTIVE);
        when(agreementDao.findById(7L)).thenReturn(Optional.of(stored));
        when(agreementDao.update(any())).thenReturn(1);
        service.terminate(7L, LocalDate.of(2026, 12, 31));
        verify(auditDao).insert(eq("AGREEMENT"), eq(7L), eq("TERMINATED"), contains("ACTIVE"), contains("2026-12-31"), any(), any(), any());
    }

    @Test
    @DisplayName("Payment record and void are audited; a rejected overpayment writes nothing")
    void paymentOperations_areAudited() throws Exception {
        AuditService audit = enabled();
        PaymentDao paymentDao = mock(PaymentDao.class);
        RentChargeDao chargeDao = mock(RentChargeDao.class);
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(charge(5L, LocalDate.of(2026, 10, 1), "100.00", "0")));
        when(paymentDao.insert(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setPaymentId(11L);
            return p;
        });
        PaymentService service = new PaymentService(paymentDao, chargeDao, mock(ReportingDao.class),
                new PassThroughTransactionManager(), audit, CLOCK, TestAuth.admin());
        service.recordPayment(5L, new BigDecimal("40.00"), TODAY, PaymentMethod.UPI, null, null);
        verify(auditDao).insert(eq("PAYMENT"), eq(11L), eq("RECORDED"), isNull(), contains("\"amount\":40.00"), any(), any(), any());

        assertThrows(BusinessRuleException.class,
                () -> service.recordPayment(5L, new BigDecimal("500.00"), TODAY, PaymentMethod.UPI, null, null));
        verify(auditDao, times(1)).insert(eq("PAYMENT"), anyLong(), eq("RECORDED"), any(), any(), any(), any(), any());

        Payment stored = new Payment(5L, new BigDecimal("40.00"), TODAY, PaymentMethod.UPI, null, null);
        stored.setPaymentId(11L);
        when(paymentDao.findById(11L)).thenReturn(Optional.of(stored));
        when(paymentDao.markVoided(anyLong(), anyString(), any())).thenReturn(1);
        service.voidPayment(11L, "typo");
        verify(auditDao).insert(eq("PAYMENT"), eq(11L), eq("VOIDED"), contains("VALID"), contains("typo"), any(), any(), any());
    }

    @Test
    @DisplayName("A failing audit insert aborts the audited operation (they commit or roll back together)")
    void auditFailure_propagatesSoTheTransactionRollsBack() {
        AuditService audit = enabled();
        doThrow(new com.estateflow.exception.DataAccessException("audit insert failed", new java.sql.SQLException("x")))
                .when(auditDao).insert(any(), anyLong(), any(), any(), any(), any(), any(), any());
        PaymentDao paymentDao = mock(PaymentDao.class);
        RentChargeDao chargeDao = mock(RentChargeDao.class);
        when(chargeDao.findByIdForUpdate(5L)).thenReturn(Optional.of(charge(5L, LocalDate.of(2026, 10, 1), "100.00", "0")));
        when(paymentDao.insert(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setPaymentId(1L);
            return p;
        });
        PaymentService service = new PaymentService(paymentDao, chargeDao, mock(ReportingDao.class),
                new PassThroughTransactionManager(), audit, CLOCK, TestAuth.admin());
        assertThrows(com.estateflow.exception.DataAccessException.class,
                () -> service.recordPayment(5L, BigDecimal.TEN, TODAY, PaymentMethod.CASH, null, null));
    }
}
