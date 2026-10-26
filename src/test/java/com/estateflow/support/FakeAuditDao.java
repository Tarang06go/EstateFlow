package com.estateflow.support;

import com.estateflow.dao.AuditDao;
import com.estateflow.dto.AuditEntry;
import com.estateflow.exception.DataAccessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** In-memory audit DAO for unit tests: keeps entries (newest first on read) and can be told to fail. */
public class FakeAuditDao implements AuditDao {

    public final List<AuditEntry> entries = new ArrayList<>();
    public boolean failInserts;
    public boolean actorSupported = true;
    public Boolean lastIncludeUserEvents;
    public String lastActorFilter;
    private long nextId = 1;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportsActor() {
        return actorSupported;
    }

    @Override
    public void insert(String type, long id, String action, String oldJson, String newJson, LocalDateTime at,
                       Long actorUserId, String actorUsername) {
        if (failInserts) {
            throw new DataAccessException("simulated audit failure", new java.sql.SQLException("boom", "HY000"));
        }
        entries.add(new AuditEntry(nextId++, at, type, id, action, oldJson, newJson,
                actorSupported ? actorUserId : null, actorSupported ? actorUsername : null));
    }

    @Override
    public List<AuditEntry> find(String type, Long id, int limit) {
        return search(type, id, null, null, true, limit);
    }

    @Override
    public List<AuditEntry> search(String type, Long id, String action, String actor, boolean includeUserEvents, int limit) {
        lastIncludeUserEvents = includeUserEvents;
        lastActorFilter = actor;
        List<AuditEntry> out = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && out.size() < limit; i--) {
            AuditEntry e = entries.get(i);
            if ((type == null || e.entityType().equals(type)) && (id == null || e.entityId() == id)
                    && (action == null || e.action().equals(action))
                    && (actor == null || actor.equalsIgnoreCase(e.actorUsername()))
                    && (includeUserEvents || !"USER".equals(e.entityType()))) {
                out.add(e);
            }
        }
        return out;
    }

    public List<String> actions() {
        return entries.stream().map(AuditEntry::action).toList();
    }
}
