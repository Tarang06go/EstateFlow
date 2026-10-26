package com.estateflow.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** History of an extension (agreementId = the extended lease) or a renewal (agreementId = the new lease, relatedAgreementId = the old one). */
public record LeaseChange(Long changeId, long agreementId, String changeType, Long relatedAgreementId,
                          LocalDate oldStartDate, LocalDate oldEndDate, BigDecimal oldMonthlyRent,
                          LocalDate newStartDate, LocalDate newEndDate, BigDecimal newMonthlyRent,
                          Long actorUserId, String actorUsername, String note, LocalDateTime createdAt) {
}
