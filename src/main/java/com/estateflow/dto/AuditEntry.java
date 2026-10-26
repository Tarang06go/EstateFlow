package com.estateflow.dto;

import java.time.LocalDateTime;

/** One row of the audit trail; values are JSON text. Timestamps are UTC. The actor is null for system/setup events and pre-v1.3 rows. */
public record AuditEntry(long auditId, LocalDateTime createdAt, String entityType, long entityId, String action,
                         String oldValue, String newValue, Long actorUserId, String actorUsername) {
}
