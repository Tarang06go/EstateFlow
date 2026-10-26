package com.estateflow.dao.jdbc;

import com.estateflow.dao.AuditDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.dto.AuditEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcAuditDao extends BaseJdbcDao implements AuditDao {

    public JdbcAuditDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public boolean isAvailable() {
        return exists("check audit_log table", "SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'audit_log' LIMIT 1", Binder.NONE);
    }

    @Override
    public boolean supportsActor() {
        return exists("check audit_log.actor_username", "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'audit_log' AND column_name = 'actor_username' LIMIT 1", Binder.NONE);
    }

    @Override
    public void insert(String entityType, long entityId, String action, String oldValueJson, String newValueJson,
                       LocalDateTime createdAtUtc, Long actorUserId, String actorUsername) {
        if (supportsActorCached()) {
            executeUpdate("insert audit entry", "INSERT INTO audit_log (user_id, actor_username, entity_type, entity_id, action, "
                    + "old_value, new_value, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
                ps.setObject(1, actorUserId);
                ps.setString(2, actorUsername);
                ps.setString(3, entityType);
                ps.setLong(4, entityId);
                ps.setString(5, action);
                ps.setString(6, oldValueJson);
                ps.setString(7, newValueJson);
                ps.setObject(8, createdAtUtc);
            });
        } else {
            executeUpdate("insert audit entry", "INSERT INTO audit_log (entity_type, entity_id, action, old_value, new_value, "
                    + "created_at) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
                ps.setString(1, entityType);
                ps.setLong(2, entityId);
                ps.setString(3, action);
                ps.setString(4, oldValueJson);
                ps.setString(5, newValueJson);
                ps.setObject(6, createdAtUtc);
            });
        }
    }

    private volatile Boolean actorColumn;

    private boolean supportsActorCached() {
        Boolean known = actorColumn;
        if (known == null) {
            known = supportsActor();
            actorColumn = known;
        }
        return known;
    }

    @Override
    public List<AuditEntry> find(String entityTypeOrNull, Long entityIdOrNull, int limit) {
        return search(entityTypeOrNull, entityIdOrNull, null, null, true, limit);
    }

    @Override
    public List<AuditEntry> search(String entityTypeOrNull, Long entityIdOrNull, String actionOrNull,
                                   String actorUsernameOrNull, boolean includeUserEvents, int limit) {
        boolean actor = supportsActorCached();
        if (actorUsernameOrNull != null && !actor) {
            return List.of(); // nobody can have been recorded before the v1.3 migration
        }
        StringBuilder sql = new StringBuilder("SELECT audit_id, created_at, entity_type, entity_id, action, old_value, new_value"
                + (actor ? ", user_id, actor_username" : "") + " FROM audit_log WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (entityTypeOrNull != null) {
            sql.append("AND entity_type = ? ");
            params.add(entityTypeOrNull);
        }
        if (entityIdOrNull != null) {
            sql.append("AND entity_id = ? ");
            params.add(entityIdOrNull);
        }
        if (actionOrNull != null) {
            sql.append("AND action = ? ");
            params.add(actionOrNull);
        }
        if (actorUsernameOrNull != null) {
            sql.append("AND actor_username = ? ");
            params.add(actorUsernameOrNull);
        }
        if (!includeUserEvents) {
            sql.append("AND entity_type <> 'USER' ");
        }
        sql.append("ORDER BY audit_id DESC LIMIT ?");
        params.add(limit);
        return queryList("list audit entries", sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, rs -> new AuditEntry(rs.getLong("audit_id"), dateTime(rs, "created_at"), rs.getString("entity_type"),
                rs.getLong("entity_id"), rs.getString("action"), rs.getString("old_value"), rs.getString("new_value"),
                actor ? nullableLong(rs, "user_id") : null, actor ? rs.getString("actor_username") : null));
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
