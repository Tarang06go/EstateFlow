package com.estateflow.dao;

import com.estateflow.model.RentCharge;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RentChargeDao {
    void insertAll(List<RentCharge> charges);

    /** Charges of the agreement with amountPaid (valid payments only), ordered by period. */
    List<RentCharge> findByAgreement(Long agreementId);

    Optional<RentCharge> findById(Long chargeId);

    /** Locking read; only valid inside a transaction and must be its first data access. */
    Optional<RentCharge> findByIdForUpdate(Long chargeId);

    /** Cancels live charges with period_start after the date; returns matched rows. */
    int cancelAfter(Long agreementId, LocalDate date, LocalDateTime at);
}
