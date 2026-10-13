package com.estateflow.model;

import com.estateflow.model.enums.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentLogicTest {

    @Test
    @DisplayName("BR-25: receiptNumber formats RCP-<year>-<id padded 6> using paymentDate year")
    void testReceiptNumber() {
        Payment payment = new Payment(
                100L,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 7, 15),
                PaymentMethod.UPI,
                "UPI-123456",
                "July rent"
        );
        payment.setPaymentId(42L);

        assertEquals("RCP-2026-000042", payment.receiptNumber());

        // Exception if ID is null
        Payment unpersisted = new Payment(
                100L,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 7, 15),
                PaymentMethod.CASH,
                null,
                null
        );
        assertThrows(IllegalStateException.class, unpersisted::receiptNumber);
    }

    @Test
    @DisplayName("BR-25: markVoided sets void reason and prevents duplicate voiding")
    void testMarkVoided() {
        Payment payment = new Payment(
                100L,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 7, 15),
                PaymentMethod.BANK_TRANSFER,
                "TXN-789",
                "Rent"
        );
        payment.setPaymentId(10L);

        assertFalse(payment.isVoid());

        LocalDateTime now = LocalDateTime.now();
        payment.markVoided("Wrong account credited", now);

        assertTrue(payment.isVoid());
        assertEquals("Wrong account credited", payment.getVoidReason());
        assertEquals(now, payment.getVoidedAt());

        // Attempting to void again throws IllegalStateException
        assertThrows(IllegalStateException.class, () ->
                payment.markVoided("Another reason", LocalDateTime.now()));
    }
}
