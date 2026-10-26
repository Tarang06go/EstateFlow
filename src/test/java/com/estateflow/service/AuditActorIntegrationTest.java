package com.estateflow.service;

import com.estateflow.dao.jdbc.JdbcAuditDao;
import com.estateflow.dto.AuditEntry;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.exception.DataAccessException;
import com.estateflow.model.AppUser;
import com.estateflow.model.Owner;
import com.estateflow.model.enums.Role;
import com.estateflow.dao.jdbc.JdbcUserDao;
import com.estateflow.db.JdbcTransactionManager;
import com.estateflow.security.PasswordHasher;
import com.estateflow.support.Fixtures;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL (estateflow_test only). Needs v1.1, v1.2 and v1.3 migrations on the test schema. */
class AuditActorIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void requireMigrations() throws Exception {
        Assumptions.assumeTrue(ctx.audit().recordsActor(),
                "apply db/migrations/v1.1, v1.2 and v1.3 to estateflow_test to run the Phase 2 integration tests");
        ctx.session().logout();
        ctx.auth().createInitialAdministrator("boss", "Integration-Pass-1");
        ctx.auth().login("boss", "Integration-Pass-1");
    }

    private AppUser createAndSignIn(String username, String password, Role role) throws Exception {
        AppUser user = ctx.users().createUser(username, password, role);
        ctx.auth().logout();
        ctx.auth().login(username, password);
        return user;
    }

    private void signInBoss() throws Exception {
        ctx.auth().logout();
        ctx.auth().login("boss", "Integration-Pass-1");
    }

    @Test
    void dataChangesAreStoredWithTheSignedInUsersIdAndUsername() throws Exception {
        AppUser boss = ctx.session().currentUser().orElseThrow();
        Owner owner = Fixtures.owner(ctx);
        AuditEntry created = ctx.audit().find("OWNER", owner.getId(), 10).get(0);
        assertEquals("CREATED", created.action());
        assertEquals(boss.id(), created.actorUserId());
        assertEquals("boss", created.actorUsername());
        assertNotNull(created.createdAt());

        // a different user: the entry follows the session, not the previous user
        AppUser mia = createAndSignIn("mia.m", "Manager-Pass-12345", Role.MANAGER);
        ctx.owners().deactivate(owner.getId());
        AuditEntry deactivated = ctx.audit().find("OWNER", owner.getId(), 10).get(0);
        assertEquals("DEACTIVATED", deactivated.action());
        assertEquals(mia.id(), deactivated.actorUserId());
        assertEquals("mia.m", deactivated.actorUsername());
    }

    @Test
    void accountEventsAreAuditedAndVisibleToAdministratorsOnly() throws Exception {
        AppUser staff = ctx.users().createUser("staff.one", "Staff-Pass-123456", Role.VIEWER);
        ctx.users().changeRole(staff.id(), Role.MANAGER);
        ctx.users().setActive(staff.id(), false);
        ctx.users().resetPassword(staff.id(), "Fresh-Pass-7654321");

        List<AuditEntry> mine = ctx.audit().find("USER", staff.id(), 20);
        assertEquals(List.of("PASSWORD_RESET", "DEACTIVATED", "ROLE_CHANGED", "CREATED"), mine.stream().map(AuditEntry::action).toList());
        assertTrue(mine.stream().allMatch(e -> "boss".equals(e.actorUsername())));
        assertTrue(mine.stream().noneMatch(e -> String.valueOf(e.newValue()).contains("Fresh-Pass") || String.valueOf(e.newValue()).contains("pbkdf2")));

        ctx.users().setActive(staff.id(), true);
        createAndSignIn("mgr.view", "Manager-Pass-12345", Role.MANAGER); // signs boss out, manager in
        assertThrows(AuthorizationException.class, () -> ctx.audit().find("USER", staff.id(), 20));
        assertTrue(ctx.audit().find(null, null, 100).stream().noneMatch(e -> "USER".equals(e.entityType())),
                "managers never see account events");
    }

    @Test
    void historyCanBeFilteredByUser() throws Exception {
        Fixtures.owner(ctx); // by boss
        createAndSignIn("filter.mgr", "Manager-Pass-12345", Role.MANAGER);
        Fixtures.owner(ctx); // by filter.mgr
        List<AuditEntry> byMgr = ctx.audit().search("OWNER", null, null, "Filter.Mgr", 50);
        assertEquals(1, byMgr.size());
        assertEquals("filter.mgr", byMgr.get(0).actorUsername());
        assertEquals(2, ctx.audit().search("OWNER", null, null, null, 50).size());
        assertTrue(ctx.audit().search("OWNER", null, null, "nobody", 50).isEmpty());
    }

    @Test
    void signInAndSignOutAreRecorded() throws Exception {
        signInBoss();
        ctx.auth().logout();
        ctx.auth().login("boss", "Integration-Pass-1");
        List<String> actions = ctx.audit().find("USER", null, 50).stream().map(AuditEntry::action).toList();
        assertTrue(actions.contains("SIGNED_IN") && actions.contains("SIGNED_OUT"));
        try {
            ctx.auth().logout();
            ctx.auth().login("boss", "wrong-password-1");
            fail("expected failure");
        } catch (com.estateflow.exception.AuthenticationException expected) {
            // the failed attempt is audited for the known account, with no actor and no password
        }
        ctx.auth().login("boss", "Integration-Pass-1");
        AuditEntry failed = ctx.audit().search("USER", null, "SIGN_IN_FAILED", null, 5).get(0);
        assertNull(failed.actorUsername());
        assertFalse(String.valueOf(failed.newValue()).contains("wrong-password-1"));
    }

    @Test
    void failedAuditWriteRollsBackAnAccountChange() throws Exception {
        AuditService failing = new AuditService(new V11IntegrationTest.FailingAuditDao(), Clock.systemUTC(), ctx.authorizer());
        UserManagementService users = new UserManagementService(new JdbcUserDao(ctx.databaseManager()), new PasswordHasher(2_000),
                new JdbcTransactionManager(ctx.databaseManager()), ctx.authorizer(), failing);
        assertThrows(DataAccessException.class, () -> users.createUser("ghost.user", "Ghost-Pass-123456", Role.VIEWER));
        assertTrue(ctx.users().listUsers().stream().noneMatch(u -> u.username().equals("ghost.user")),
                "the account must not survive a failed audit insert");
    }

    @Test
    void withoutTheV13ColumnEntriesStillWriteAndOldRowsReadWithoutAUser() throws Exception {
        var db = ctx.databaseManager();
        Owner owner = Fixtures.owner(ctx);
        try (var h = db.acquire(); Statement st = h.connection().createStatement()) {
            st.execute("ALTER TABLE audit_log DROP INDEX idx_audit_actor, DROP INDEX idx_audit_user, DROP COLUMN actor_username");
        }
        try {
            JdbcAuditDao legacyDao = new JdbcAuditDao(db); // fresh instance: re-checks the schema
            assertFalse(legacyDao.supportsActor());
            AuditService legacy = new AuditService(legacyDao, Clock.fixed(Instant.parse("2026-10-22T09:30:00Z"), ZoneOffset.UTC), ctx.authorizer());
            legacy.record("OWNER", owner.getId(), "UPDATED", null, AuditService.details("name", "Legacy"));
            AuditEntry newest = legacy.find("OWNER", owner.getId(), 5).get(0);
            assertEquals("UPDATED", newest.action());
            assertNull(newest.actorUsername());
            assertTrue(legacy.search("OWNER", null, null, "boss", 5).isEmpty());
            assertFalse(legacy.recordsActor());
        } finally {
            try (var h = db.acquire(); Statement st = h.connection().createStatement()) {
                st.execute("ALTER TABLE audit_log ADD COLUMN actor_username VARCHAR(50) NULL AFTER user_id, "
                        + "ADD INDEX idx_audit_user (user_id), ADD INDEX idx_audit_actor (actor_username)");
            }
        }
    }
}
