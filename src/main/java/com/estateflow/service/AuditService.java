package com.estateflow.service;

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

    private final AuditDao dao;
    private final Clock clock;
    private volatile Boolean available;

    public AuditService(AuditDao dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    /** A disabled instance for tests and for contexts without auditing. */
    public static AuditService noop() {
        return new AuditService(null, null);
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

    public void record(String entityType, long entityId, String action, Map<String, ?> oldValue, Map<String, ?> newValue) {
        if (!isEnabled()) {
            return;
        }
        dao.insert(entityType, entityId, action, toJson(oldValue), toJson(newValue),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    /** Newest first. Returns an empty list when auditing is unavailable. */
    public List<AuditEntry> find(String entityTypeOrNull, Long entityIdOrNull, int limit) {
        return isEnabled() ? dao.find(entityTypeOrNull, entityIdOrNull, limit) : List.of();
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
