package com.estateflow.dao;

import com.estateflow.model.Payment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentDao {
    Payment insert(Payment payment);

    Optional<Payment> findById(Long paymentId);

    /** All payments of the charge, including voided ones. */
    List<Payment> findByCharge(Long chargeId);

    List<Payment> findByDateRange(LocalDate from, LocalDate to);

    /**
     * Voids a payment only if it is not already voided.
     *
     * @return matched rows; zero means the payment is missing or already voided
     */
    int markVoided(Long paymentId, String reason, LocalDateTime at);

    /** True if a valid payment exists on a live-or-cancelled charge of the agreement starting after the date. */
    boolean existsValidForChargesAfter(Long agreementId, LocalDate date);
}
