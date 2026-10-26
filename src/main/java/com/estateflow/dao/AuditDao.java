package com.estateflow.dao;

import com.estateflow.dto.AuditEntry;

import java.time.LocalDateTime;
import java.util.List;

/** v1.1 audit trail (see db/migrations/v1.1_audit_log.sql). Append-only: no update, no delete. */
public interface AuditDao {
    /** True when the audit_log table exists, i.e. the v1.1 migration has been applied. */
    boolean isAvailable();

    /** True when the v1.3 migration (audit_log.actor_username) has been applied. */
    boolean supportsActor();

    /** The actor is dropped (legacy insert) when {@link #supportsActor()} is false. */
    void insert(String entityType, long entityId, String action, String oldValueJson, String newValueJson,
                LocalDateTime createdAtUtc, Long actorUserId, String actorUsername);

    /** Newest first; null filters match everything. */
    List<AuditEntry> find(String entityTypeOrNull, Long entityIdOrNull, int limit);

    /**
     * Filtered, newest first. All filters are optional. {@code actorUsername} is an exact (case-insensitive) match.
     * With {@code includeUserEvents} false, account events (entity type USER) are excluded.
     */
    List<AuditEntry> search(String entityTypeOrNull, Long entityIdOrNull, String actionOrNull, String actorUsernameOrNull,
                            boolean includeUserEvents, int limit);
}
