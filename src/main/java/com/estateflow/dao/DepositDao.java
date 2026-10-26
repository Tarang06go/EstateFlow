package com.estateflow.dao;

import com.estateflow.dto.DepositActivityRow;
import com.estateflow.dto.DepositRow;
import com.estateflow.dto.DepositTotals;
import com.estateflow.model.DepositTransaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** The deposit ledger. Append-only: transactions are voided, never updated or deleted. */
public interface DepositDao {

    /** True when the v1.5 tables exist. */
    boolean isAvailable();

    Optional<Long> findDepositId(long agreementId);

    /** Throws DuplicateKeyException when the agreement already has a deposit (one per agreement). */
    long insertDeposit(long agreementId);

    /** Locks the deposit row for the rest of the transaction so balance checks cannot race. */
    boolean lockDeposit(long depositId);

    DepositTotals totals(long depositId);

    Optional<LocalDate> firstCollectionDate(long depositId);

    long insertTransaction(DepositTransaction txn);

    Optional<DepositTransaction> findTransaction(long txnId);

    /** Marks an unvoided transaction void; returns matched rows (0 when already voided or missing). */
    int voidTransaction(long txnId, String reason, LocalDateTime voidedAtUtc);

    /** All entries including voided ones, oldest first. */
    List<DepositTransaction> transactions(long depositId);

    /** Deposits that exist, plus required-but-uncollected deposits of leases that have not ended. */
    List<DepositRow> rows(Long propertyIdOrNull, Long tenantIdOrNull, LocalDate today);

    Optional<DepositRow> rowForAgreement(long agreementId, LocalDate today);

    /** Non-voided collections, deductions and refunds dated in [from, to], newest first. */
    List<DepositActivityRow> activity(LocalDate from, LocalDate to, Long propertyIdOrNull, Long tenantIdOrNull);
}
