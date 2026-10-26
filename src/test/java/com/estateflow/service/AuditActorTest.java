package com.estateflow.service;

import com.estateflow.dto.AuditEntry;
import com.estateflow.exception.AuthenticationException;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.exception.DataAccessException;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;
import com.estateflow.security.Authorizer;
import com.estateflow.security.PasswordHasher;
import com.estateflow.security.Session;
import com.estateflow.support.FakeAuditDao;
import com.estateflow.support.FakeUserDao;
import com.estateflow.support.TestAuth;
import com.estateflow.util.ActivityText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 2: audit entries carry the signed-in user; account/sign-in events are audited; access and failures are safe. */
class AuditActorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T09:30:00Z"), ZoneOffset.UTC);

    private final PasswordHasher hasher = new PasswordHasher(2_000);
    private FakeAuditDao auditDao;
    private FakeUserDao userDao;
    private Session session;
    private Authorizer auth;
    private AuditService audit;
    private AuthService authService;
    private UserManagementService users;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        auditDao = new FakeAuditDao();
        userDao = new FakeUserDao();
        session = new Session();
        auth = new Authorizer(session);
        audit = new AuditService(auditDao, CLOCK, auth);
        authService = new AuthService(userDao, hasher, session, new PassThroughTransactionManager(), CLOCK, audit);
        users = new UserManagementService(userDao, hasher, new PassThroughTransactionManager(), auth, audit);
        admin = userDao.insert("admin", hasher.hash("Admin-Pass-2026"), Role.ADMIN);
    }

    private void signInAdmin() throws Exception {
        authService.login("admin", "Admin-Pass-2026");
        auditDao.entries.clear();
    }

    // ---- actor capture ----

    @Test
    void recordStoresTheSignedInUsersIdAndUsernameFromTheSession() throws Exception {
        signInAdmin();
        audit.record("OWNER", 5, "CREATED", null, AuditService.details("name", "Asha"));
        AuditEntry e = auditDao.entries.get(0);
        assertEquals(admin.id(), e.actorUserId());
        assertEquals("admin", e.actorUsername());
        assertEquals("OWNER", e.entityType());
        assertEquals(5, e.entityId());
        assertEquals("CREATED", e.action());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 22, 9, 30), e.createdAt());
    }

    @Test
    void entriesWithNobodySignedInCarryNoUser() {
        audit.record("USER", 1, "CREATED", null, AuditService.details("via", "setup"));
        assertNull(auditDao.entries.get(0).actorUserId());
        assertNull(auditDao.entries.get(0).actorUsername());
    }

    @Test
    void recordFailsClosedButBestEffortNeverThrows() {
        auditDao.failInserts = true;
        assertThrows(DataAccessException.class, () -> audit.record("OWNER", 1, "CREATED", null, null));
        assertDoesNotThrow(() -> audit.recordBestEffort("USER", 1, "SIGNED_IN", null, null));
        assertTrue(auditDao.entries.isEmpty());
    }

    // ---- who can read the history ----

    @Test
    void viewerAndSignedOutCannotReadTheHistory() {
        for (Authorizer denied : new Authorizer[]{TestAuth.as(Role.VIEWER), TestAuth.signedOut()}) {
            AuditService s = new AuditService(auditDao, CLOCK, denied);
            assertThrows(AuthorizationException.class, () -> s.find(null, null, 10));
            assertThrows(AuthorizationException.class, () -> s.search(null, null, null, "admin", 10));
        }
    }

    @Test
    void managerSeesOperationalHistoryButNotAccountEvents() {
        auditDao.insert("OWNER", 1, "CREATED", null, null, java.time.LocalDateTime.now(CLOCK), 1L, "admin");
        auditDao.insert("USER", 2, "ROLE_CHANGED", null, null, java.time.LocalDateTime.now(CLOCK), 1L, "admin");
        AuditService asManager = new AuditService(auditDao, CLOCK, TestAuth.as(Role.MANAGER));
        List<AuditEntry> seen = asManager.find(null, null, 10);
        assertEquals(1, seen.size());
        assertEquals("OWNER", seen.get(0).entityType());
        assertFalse(auditDao.lastIncludeUserEvents);
        assertThrows(AuthorizationException.class, () -> asManager.search("USER", null, null, null, 10));

        AuditService asAdmin = new AuditService(auditDao, CLOCK, TestAuth.admin());
        assertEquals(2, asAdmin.find(null, null, 10).size());
        assertTrue(auditDao.lastIncludeUserEvents);
    }

    @Test
    void userFilterIsNormalisedAndBlankMeansNoFilter() {
        AuditService asAdmin = new AuditService(auditDao, CLOCK, TestAuth.admin());
        asAdmin.search(null, null, null, "  Admin ", 10);
        assertEquals("admin", auditDao.lastActorFilter);
        asAdmin.search(null, null, null, "   ", 10);
        assertNull(auditDao.lastActorFilter);
    }

    // ---- user management is audited ----

    @Test
    void userManagementActionsAreRecordedWithTheActingAdminAndNoSecrets() throws Exception {
        signInAdmin();
        AppUser sam = users.createUser("sam.k", "Quick-Pass-123456", Role.VIEWER);
        users.changeRole(sam.id(), Role.MANAGER);
        users.setActive(sam.id(), false);
        users.setActive(sam.id(), true);
        users.resetPassword(sam.id(), "Brand-New-Pass-77");

        assertEquals(List.of("CREATED", "ROLE_CHANGED", "DEACTIVATED", "REACTIVATED", "PASSWORD_RESET"), auditDao.actions());
        for (AuditEntry e : auditDao.entries) {
            assertEquals("USER", e.entityType());
            assertEquals(sam.id(), e.entityId());
            assertEquals("admin", e.actorUsername());
            assertEquals(admin.id(), e.actorUserId());
            String all = String.valueOf(e.oldValue()) + e.newValue();
            assertFalse(all.contains("Quick-Pass") || all.contains("Brand-New") || all.contains("pbkdf2"), "no secrets in audit");
        }
        AuditEntry role = auditDao.entries.get(1);
        assertTrue(role.oldValue().contains("VIEWER") && role.newValue().contains("MANAGER"));
    }

    @Test
    void refusedUserManagementCallsLeaveNoAuditEntries() throws Exception {
        UserManagementService asManager = new UserManagementService(userDao, hasher, new PassThroughTransactionManager(),
                TestAuth.as(Role.MANAGER), audit);
        assertThrows(AuthorizationException.class, () -> asManager.createUser("evil", "Evil-Pass-1234567", Role.ADMIN));
        assertThrows(AuthorizationException.class, () -> asManager.setActive(admin.id(), false));
        assertTrue(auditDao.entries.isEmpty());
    }

    @Test
    void rejectedBusinessRulesAreNotAudited() throws Exception {
        signInAdmin();
        assertThrows(com.estateflow.exception.BusinessRuleException.class, () -> users.setActive(admin.id(), false));
        assertTrue(auditDao.entries.isEmpty(), "a refused change must not look like it happened");
    }

    // ---- sign-in activity ----

    @Test
    void signInAndSignOutAreRecordedForTheUser() throws Exception {
        authService.login("admin", "Admin-Pass-2026");
        authService.logout();
        assertEquals(List.of("SIGNED_IN", "SIGNED_OUT"), auditDao.actions());
        assertEquals("admin", auditDao.entries.get(0).actorUsername());
        assertEquals("admin", auditDao.entries.get(1).actorUsername());
        authService.logout(); // already signed out: nothing more to record
        assertEquals(2, auditDao.entries.size());
    }

    @Test
    void failedSignInIsRecordedOnlyForKnownAccountsAndWithoutAnActor() {
        assertThrows(AuthenticationException.class, () -> authService.login("admin", "wrong-password-1"));
        assertThrows(AuthenticationException.class, () -> authService.login("ghost", "wrong-password-1"));
        assertEquals(List.of("SIGN_IN_FAILED"), auditDao.actions());
        AuditEntry e = auditDao.entries.get(0);
        assertEquals(admin.id(), e.entityId());
        assertNull(e.actorUsername());
        assertFalse(e.newValue().contains("wrong-password-1"));
    }

    @Test
    void anAuditFailureNeverBlocksSigningInOrOut() throws Exception {
        auditDao.failInserts = true;
        assertEquals("admin", authService.login("admin", "Admin-Pass-2026").username());
        assertTrue(session.isAuthenticated());
        assertDoesNotThrow(() -> authService.logout());
        assertFalse(session.isAuthenticated());
    }

    @Test
    void firstAdministratorSetupIsAuditedWithoutAnActor() throws Exception {
        FakeUserDao empty = new FakeUserDao();
        AuthService fresh = new AuthService(empty, hasher, new Session(), new PassThroughTransactionManager(), CLOCK, audit);
        fresh.createInitialAdministrator("owner.admin", "Setup-Pass-2026");
        AuditEntry e = auditDao.entries.get(0);
        assertEquals("CREATED", e.action());
        assertNull(e.actorUsername());
        assertTrue(e.newValue().contains("first-run setup"));
    }

    @Test
    void auditFailureDuringUserCreationPropagatesSoTheChangeIsNotKept() throws Exception {
        signInAdmin();
        auditDao.failInserts = true;
        assertThrows(DataAccessException.class, () -> users.createUser("newbie", "Rookie-Pass-12345", Role.VIEWER));
    }

    // ---- display ----

    @Test
    void activityTextNamesTheUserWhereItMatters() {
        AuditEntry change = new AuditEntry(1, java.time.LocalDateTime.of(2026, 10, 22, 9, 0), "PAYMENT", 12, "VOIDED", null, null, 3L, "meera");
        assertEquals("Payment #12 voided by meera", ActivityText.describe(change));
        AuditEntry old = new AuditEntry(2, java.time.LocalDateTime.of(2026, 10, 22, 9, 0), "PAYMENT", 12, "RECORDED", null, null, null, null);
        assertEquals("Payment #12 recorded", ActivityText.describe(old));
        AuditEntry signIn = new AuditEntry(3, java.time.LocalDateTime.of(2026, 10, 22, 9, 0), "USER", 3, "SIGNED_IN", null, null, 3L, "meera");
        assertEquals("User account #3 signed in", ActivityText.describe(signIn));
    }
}
