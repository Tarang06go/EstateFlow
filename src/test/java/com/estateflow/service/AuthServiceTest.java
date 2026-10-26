package com.estateflow.service;

import com.estateflow.exception.*;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;
import com.estateflow.security.PasswordHasher;
import com.estateflow.security.Session;
import com.estateflow.support.FakeUserDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);

    private final PasswordHasher hasher = new PasswordHasher(2_000);
    private FakeUserDao dao;
    private Session session;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        dao = new FakeUserDao();
        session = new Session();
        auth = new AuthService(dao, hasher, session, new PassThroughTransactionManager(), CLOCK, AuditService.noop());
        dao.insert("alice", hasher.hash("Alice-Pass-123"), Role.MANAGER);
    }

    @Test
    void successfulLoginStartsSessionAndRecordsLastLogin() throws Exception {
        AppUser user = auth.login("  Alice ", "Alice-Pass-123");
        assertEquals("alice", user.username());
        assertTrue(session.isAuthenticated());
        assertEquals(Role.MANAGER, session.currentUser().orElseThrow().role());
        assertEquals(1, dao.touchCount);
        assertNotNull(dao.findById(user.id()).orElseThrow().lastLoginAt());
    }

    @Test
    void wrongPasswordFailsGenericallyAndLeavesSessionEmpty() {
        AuthenticationException e = assertThrows(AuthenticationException.class, () -> auth.login("alice", "wrong-password1"));
        assertEquals(AuthService.GENERIC_FAILURE, e.getMessage());
        assertFalse(session.isAuthenticated());
        assertEquals(0, dao.touchCount);
    }

    @Test
    void unknownUsernameGivesTheSameMessageAsWrongPassword() {
        AuthenticationException unknown = assertThrows(AuthenticationException.class, () -> auth.login("nobody", "Alice-Pass-123"));
        AuthenticationException wrong = assertThrows(AuthenticationException.class, () -> auth.login("alice", "nope-nope-123"));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertFalse(session.isAuthenticated());
    }

    @Test
    void inactiveAccountCannotSignInEvenWithTheRightPassword() throws Exception {
        long id = dao.findCredentialsByUsername("alice").orElseThrow().user().id();
        dao.updateActive(id, false);
        AuthenticationException e = assertThrows(AuthenticationException.class, () -> auth.login("alice", "Alice-Pass-123"));
        assertEquals(AuthService.GENERIC_FAILURE, e.getMessage());
        assertFalse(session.isAuthenticated());
        assertEquals(0, dao.touchCount);
    }

    @Test
    void blankInputIsRejectedWithoutTouchingTheSession() {
        assertThrows(AuthenticationException.class, () -> auth.login("", "x"));
        assertThrows(AuthenticationException.class, () -> auth.login("alice", ""));
        assertThrows(AuthenticationException.class, () -> auth.login(null, null));
        assertFalse(session.isAuthenticated());
    }

    @Test
    void failedLoginAfterASuccessfulOneDoesNotKeepThePreviousUser() throws Exception {
        auth.login("alice", "Alice-Pass-123");
        assertTrue(session.isAuthenticated());
        assertThrows(AuthenticationException.class, () -> auth.login("alice", "bad-password-1"));
        assertFalse(session.isAuthenticated());
    }

    @Test
    void logoutClearsTheSession() throws Exception {
        auth.login("alice", "Alice-Pass-123");
        auth.logout();
        assertFalse(session.isAuthenticated());
        assertTrue(session.currentUser().isEmpty());
    }

    @Test
    void passwordReauthenticationRejectsWrongPasswordWithoutEndingSession() throws Exception {
        auth.login("alice", "Alice-Pass-123");
        assertThrows(AuthenticationException.class, () -> auth.reauthenticateCurrent("wrong-password-1"));
        assertTrue(session.isAuthenticated());
        assertEquals("alice", auth.reauthenticateCurrent("Alice-Pass-123").username());
    }

    @Test
    void firstAdministratorSetupWorksOnlyWhileNoActiveAdminExists() throws Exception {
        assertTrue(auth.needsInitialSetup());
        AppUser admin = auth.createInitialAdministrator("Owner.Admin", "Setup-Pass-2026");
        assertEquals(Role.ADMIN, admin.role());
        assertEquals("owner.admin", admin.username());
        assertNotEquals("Setup-Pass-2026", dao.storedHash(admin.id()));
        assertTrue(hasher.verify("Setup-Pass-2026", dao.storedHash(admin.id())));
        assertFalse(auth.needsInitialSetup());
        assertThrows(BusinessRuleException.class, () -> auth.createInitialAdministrator("second", "Another-Pass-1"));
    }

    @Test
    void setupRejectsWeakPasswordsAndDuplicateUsernames() throws Exception {
        assertThrows(ValidationException.class, () -> auth.createInitialAdministrator("admin1", "short"));
        assertThrows(DuplicateEntityException.class, () -> auth.createInitialAdministrator("alice", "Setup-Pass-2026"));
        assertTrue(auth.needsInitialSetup());
    }

    @Test
    void missingUserTableGivesASetupMessage() {
        dao.available = false;
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> auth.ensureReady());
        assertTrue(e.getMessage().contains("v1.2_app_user.sql"));
    }
}
