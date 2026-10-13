package com.estateflow.dto;

import java.time.LocalDateTime;

/** One row of the audit trail; values are JSON text. Timestamps are UTC. */
public record AuditEntry(long auditId, LocalDateTime createdAt, String entityType, long entityId, String action,
                         String oldValue, String newValue) {
}
