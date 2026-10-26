package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An ended, un-renewed lease that still has rent owing or a deposit held. */
public record LeaseAttentionRow(long agreementId, String propertyName, String tenantName, LocalDate effectiveEnd,
                                long daysSinceEnd, boolean terminatedEarly, BigDecimal rentOutstanding,
                                BigDecimal depositBalance) {
}
