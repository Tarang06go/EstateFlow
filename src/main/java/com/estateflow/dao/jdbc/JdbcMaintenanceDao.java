package com.estateflow.dao.jdbc;

import com.estateflow.dao.MaintenanceDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.MaintenanceRequest;
import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.model.enums.MaintenanceStatus;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcMaintenanceDao extends BaseJdbcDao implements MaintenanceDao {

    private static final String COLUMNS = "m.request_id, m.property_id, m.tenant_id, m.title, m.description, "
            + "m.priority, m.status, m.reported_date, m.resolved_date, m.assigned_to, m.cost, m.created_at, m.updated_at";

    private static final RowMapper<MaintenanceRequest> MAPPER = rs -> {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRequestId(rs.getLong("request_id"));
        r.setPropertyId(rs.getLong("property_id"));
        r.setTenantId(longOrNull(rs, "tenant_id"));
        r.setTitle(rs.getString("title"));
        r.setDescription(rs.getString("description"));
        r.setPriority(MaintenancePriority.valueOf(rs.getString("priority")));
        r.setStatus(MaintenanceStatus.valueOf(rs.getString("status")));
        r.setReportedDate(date(rs, "reported_date"));
        r.setResolvedDate(date(rs, "resolved_date"));
        r.setAssignedTo(rs.getString("assigned_to"));
        r.setCost(rs.getBigDecimal("cost"));
        r.setCreatedAt(dateTime(rs, "created_at"));
        r.setUpdatedAt(dateTime(rs, "updated_at"));
        return r;
    };

    public JdbcMaintenanceDao(DatabaseManager databaseManager) {
        super(databaseManager);
    }

    private static void bindTenant(PreparedStatement ps, int index, Long tenantId) throws SQLException {
        if (tenantId == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, tenantId);
        }
    }

    @Override
    public MaintenanceRequest insert(MaintenanceRequest r) {
        long id = insertReturningKey("insert maintenance request", "INSERT INTO maintenance_request (property_id, "
                + "tenant_id, title, description, priority, status, reported_date, resolved_date, assigned_to, cost) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setLong(1, r.getPropertyId());
            bindTenant(ps, 2, r.getTenantId());
            ps.setString(3, r.getTitle());
            ps.setString(4, r.getDescription());
            ps.setString(5, r.getPriority().name());
            ps.setString(6, r.getStatus().name());
            ps.setObject(7, r.getReportedDate());
            ps.setObject(8, r.getResolvedDate());
            ps.setString(9, r.getAssignedTo());
            ps.setBigDecimal(10, r.getCost());
        });
        r.setRequestId(id);
        return r;
    }

    @Override
    public int update(MaintenanceRequest r) {
        return executeUpdate("update maintenance request", "UPDATE maintenance_request SET title = ?, description = ?, "
                + "priority = ?, status = ?, resolved_date = ?, assigned_to = ?, cost = ? WHERE request_id = ?", ps -> {
            ps.setString(1, r.getTitle());
            ps.setString(2, r.getDescription());
            ps.setString(3, r.getPriority().name());
            ps.setString(4, r.getStatus().name());
            ps.setObject(5, r.getResolvedDate());
            ps.setString(6, r.getAssignedTo());
            ps.setBigDecimal(7, r.getCost());
            ps.setLong(8, r.getRequestId());
        });
    }

    @Override
    public Optional<MaintenanceRequest> findById(Long id) {
        return queryOne("find maintenance request", "SELECT " + COLUMNS + " FROM maintenance_request m WHERE m.request_id = ?",
                ps -> ps.setLong(1, id), MAPPER);
    }

    @Override
    public List<MaintenanceRequest> findAll() {
        return queryList("list maintenance requests", "SELECT " + COLUMNS
                + " FROM maintenance_request m ORDER BY m.reported_date DESC, m.request_id DESC", Binder.NONE, MAPPER);
    }

    @Override
    public List<MaintenanceRequest> findByProperty(Long propertyId) {
        return queryList("list requests of property", "SELECT " + COLUMNS + " FROM maintenance_request m "
                + "WHERE m.property_id = ? ORDER BY m.reported_date DESC", ps -> ps.setLong(1, propertyId), MAPPER);
    }

    @Override
    public List<MaintenanceRequest> findByStatus(MaintenanceStatus status) {
        return queryList("list requests by status", "SELECT " + COLUMNS + " FROM maintenance_request m "
                + "WHERE m.status = ? ORDER BY m.reported_date DESC", ps -> ps.setString(1, status.name()), MAPPER);
    }

    @Override
    public Map<MaintenancePriority, Integer> countOpenByPriority() {
        Map<MaintenancePriority, Integer> counts = new EnumMap<>(MaintenancePriority.class);
        for (MaintenancePriority p : MaintenancePriority.values()) {
            counts.put(p, 0);
        }
        queryList("count open requests by priority", "SELECT priority, COUNT(*) AS n FROM maintenance_request "
                + "WHERE status IN (?, ?) GROUP BY priority", ps -> {
            ps.setString(1, MaintenanceStatus.OPEN.name());
            ps.setString(2, MaintenanceStatus.IN_PROGRESS.name());
        }, rs -> {
            counts.put(MaintenancePriority.valueOf(rs.getString("priority")), rs.getInt("n"));
            return null;
        });
        return counts;
    }
}
