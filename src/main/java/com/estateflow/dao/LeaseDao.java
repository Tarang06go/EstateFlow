package com.estateflow.dao;

import com.estateflow.dto.LeaseAttentionRow;
import com.estateflow.model.LeaseChange;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Renewal links, extensions and lease history (v1.5). */
public interface LeaseDao {

    /** True when rental_agreement.renewed_from_agreement_id and lease_change exist. */
    boolean supportsRenewal();

    /** The agreement that renewed this one, if any. */
    Optional<Long> findSuccessor(long agreementId);

    /** Links a new agreement to the one it renews. Throws DuplicateKeyException if that agreement is already renewed. */
    int setRenewedFrom(long newAgreementId, long oldAgreementId);

    /** Lengthens an ACTIVE agreement; the generated effective end date follows. Returns matched rows. */
    int extendTerm(long agreementId, int newDurationMonths, LocalDate newEndDate);

    void insertChange(LeaseChange change);

    /** Changes made to this agreement and renewals that replaced it, oldest first. */
    List<LeaseChange> findChanges(long agreementId);

    /** Ended, un-renewed leases with rent still owing or a deposit still held, longest ended first. */
    List<LeaseAttentionRow> findEndedNeedingAttention(LocalDate today);
}
