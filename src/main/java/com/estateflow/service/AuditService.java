package com.estateflow.service;

import java.util.Optional;
import com.estateflow.model.AppUser;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Session;
import com.estateflow.security.Permission;
import com.estateflow.dao.AuditDao;
import com.estateflow.dto.AuditEntry;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records important operations in the v1.1 audit_log table. Entries are written inside the caller's
 * transaction, so an operation and its audit entry commit or roll back together. When the v1.1 migration
 * has not been applied the service is disabled and does nothing (the table check is made once, lazily).
 */
public class AuditService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditService.class);

    private final AuditDao dao;
    private final Clock clock;
    private volatile Boolean available;

    private final Authorizer auth;

    public AuditService(AuditDao dao, Clock clock,
            Authorizer auth) {
        this.auth = auth;
        this.dao = dao;
        this.clock = clock;
    }

    /** A disabled instance for tests and for contexts without auditing. */
    public static AuditService noop() {
        return new AuditService(null, null, new Authorizer(new Session()));
    }

    public boolean isEnabled() {
        if (dao == null) {
            return false;
        }
        Boolean known = available;
        if (known == null) {
            known = dao.isAvailable();
            available = known;
        }
        return known;
    }

    /** Builds an ordered key/value description for an audit entry: details("status", "ACTIVE", "rent", 100). */
    public static Map<String, Object> details(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }

    /**
     * Writes an entry attributed to the signed-in user (id and username come from the session, never from the
     * caller). Runs inside the caller's transaction and lets failures propagate, so a data change and its audit entry
     * commit or roll back together: nothing changes without being audited. Entries made while nobody is signed in
     * (first-administrator setup) carry no user.
     */
    public void record(String entityType, long entityId, String action, Map<String, ?> oldValue, Map<String, ?> newValue) {
        if (!isEnabled()) {
            return;
        }
        Optional<AppUser> actor = auth.session().currentUser();
        dao.insert(entityType, entityId, action, toJson(oldValue), toJson(newValue),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                actor.map(AppUser::id).orElse(null), actor.map(AppUser::username).orElse(null));
    }

    /**
     * For informational events (sign-in, sign-out, failed sign-in) that must never block the action they describe:
     * a failure to audit is logged as a warning and swallowed.
     */
    public void recordBestEffort(String entityType, long entityId, String action, Map<String, ?> oldValue, Map<String, ?> newValue) {
        try {
            record(entityType, entityId, action, oldValue, newValue);
        } catch (RuntimeException e) {
            log.warn("Could not write audit entry {} {} #{}: {}", action, entityType, entityId, e.toString());
        }
    }

    /** Newest first. Returns an empty list when auditing is unavailable. */
    public List<AuditEntry> find(String entityTypeOrNull, Long entityIdOrNull, int limit) {
        return search(entityTypeOrNull, entityIdOrNull, null, null, limit);
    }

    /**
     * Filtered history for roles with {@link Permission#VIEW_AUDIT}. Account events (entity type USER) are visible to
     * administrators only; for other roles they are excluded, and asking for them explicitly is refused.
     */
    public List<AuditEntry> search(String entityTypeOrNull, Long entityIdOrNull, String actionOrNull,
                                   String actorUsernameOrNull, int limit) {
        if (dao == null) {
            return List.of(); // the disabled no-op instance holds no data
        }
        auth.require(Permission.VIEW_AUDIT);
        boolean userEvents = auth.can(Permission.MANAGE_USERS);
        if (!userEvents && "USER".equals(entityTypeOrNull)) {
            throw new AuthorizationException("Your role does not permit this action.");
        }
        if (!isEnabled()) {
            return List.of();
        }
        String actor = actorUsernameOrNull == null || actorUsernameOrNull.isBlank() ? null
                : actorUsernameOrNull.trim().toLowerCase(java.util.Locale.ROOT);
        return dao.search(entityTypeOrNull, entityIdOrNull, actionOrNull, actor, userEvents, limit);
    }

    /** True when entries also record the signed-in user (v1.3 migration applied). */
    public boolean recordsActor() {
        return isEnabled() && dao.supportsActor();
    }

    static String toJson(Map<String, ?> map) {
        if (map == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(e.getKey())).append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append(quote(String.valueOf(v)));
            }
        }
        return sb.append('}').toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
