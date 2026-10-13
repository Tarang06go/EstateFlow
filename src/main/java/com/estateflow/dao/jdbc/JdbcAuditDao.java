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
    public void insert(String entityType, long entityId, String action, String oldValueJson, String newValueJson,
                       LocalDateTime createdAtUtc) {
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

    @Override
    public List<AuditEntry> find(String entityTypeOrNull, Long entityIdOrNull, int limit) {
        StringBuilder sql = new StringBuilder("SELECT audit_id, created_at, entity_type, entity_id, action, old_value, "
                + "new_value FROM audit_log WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (entityTypeOrNull != null) {
            sql.append("AND entity_type = ? ");
            params.add(entityTypeOrNull);
        }
        if (entityIdOrNull != null) {
            sql.append("AND entity_id = ? ");
            params.add(entityIdOrNull);
        }
        sql.append("ORDER BY audit_id DESC LIMIT ?");
        params.add(limit);
        return queryList("list audit entries", sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, rs -> new AuditEntry(rs.getLong("audit_id"), dateTime(rs, "created_at"), rs.getString("entity_type"),
                rs.getLong("entity_id"), rs.getString("action"), rs.getString("old_value"), rs.getString("new_value")));
    }
}
