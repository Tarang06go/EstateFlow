package com.estateflow.dao;

import com.estateflow.dto.AuditEntry;

import java.time.LocalDateTime;
import java.util.List;

/** v1.1 audit trail (see db/migrations/v1.1_audit_log.sql). Append-only: no update, no delete. */
public interface AuditDao {
    /** True when the audit_log table exists, i.e. the v1.1 migration has been applied. */
    boolean isAvailable();

    void insert(String entityType, long entityId, String action, String oldValueJson, String newValueJson,
                LocalDateTime createdAtUtc);

    /** Newest first; null filters match everything. */
    List<AuditEntry> find(String entityTypeOrNull, Long entityIdOrNull, int limit);
}
