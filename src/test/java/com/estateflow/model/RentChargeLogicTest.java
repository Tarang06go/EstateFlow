package com.estateflow.model;

import com.estateflow.model.enums.ChargeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RentChargeLogicTest {

    private RentCharge charge;

    @BeforeEach
    void setUp() {
        charge = new RentCharge(
                1L,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 5, 1),
                new BigDecimal("15000.00")
        );
        charge.setChargeId(100L);
    }

    @Test
    @DisplayName("getBalance calculates remaining balance correctly")
    void testGetBalance() {
        assertEquals(new BigDecimal("15000.00"), charge.getBalance());

        charge.setAmountPaid(new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("10000.00"), charge.getBalance());

        charge.setAmountPaid(new BigDecimal("15000.00"));
        assertEquals(BigDecimal.ZERO.setScale(2), charge.getBalance());
    }

    @Test
    @DisplayName("BR-20: isPayable returns true if live and due_date <= today")
    void testIsPayable() {
        // Due on May 1
        assertFalse(charge.isPayable(LocalDate.of(2026, 4, 30)));
        assertTrue(charge.isPayable(LocalDate.of(2026, 5, 1)));
        assertTrue(charge.isPayable(LocalDate.of(2026, 5, 10)));

        // Cancelled charge is never payable
        charge.setCancelledAt(LocalDateTime.of(2026, 5, 2, 10, 0));
        assertFalse(charge.isPayable(LocalDate.of(2026, 5, 10)));
    }

    @Test
    @DisplayName("status derives correct charge status")
    void testStatus() {
        LocalDate beforeDue = LocalDate.of(2026, 4, 30);
        LocalDate onDue = LocalDate.of(2026, 5, 1);
        LocalDate afterDue = LocalDate.of(2026, 5, 10);

        // Zero paid, before or on due date -> PENDING
        assertEquals(ChargeStatus.PENDING, charge.status(onDue));

        // Zero paid, past due date -> OVERDUE
        assertEquals(ChargeStatus.OVERDUE, charge.status(afterDue));

        // Partially paid, not overdue -> PARTIALLY_PAID
        charge.setAmountPaid(new BigDecimal("5000.00"));
        assertEquals(ChargeStatus.PARTIALLY_PAID, charge.status(onDue));

        // Partially paid, past due date -> OVERDUE (since balance > 0 and due_date < today)
        assertEquals(ChargeStatus.OVERDUE, charge.status(afterDue));

        // Fully paid -> PAID (regardless of date)
        charge.setAmountPaid(new BigDecimal("15000.00"));
        assertEquals(ChargeStatus.PAID, charge.status(afterDue));

        // Cancelled -> CANCELLED
        charge.setCancelledAt(LocalDateTime.now());
        assertEquals(ChargeStatus.CANCELLED, charge.status(afterDue));
    }
}
