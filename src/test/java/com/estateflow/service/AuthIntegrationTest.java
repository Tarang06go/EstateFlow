package com.estateflow.service;

import com.estateflow.exception.*;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;
import com.estateflow.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL (estateflow_test only): the app_user table, unique username, login and lockout protection. */
class AuthIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void signedOut() {
        ctx.session().logout();
    }

    private String scalar(String sql) throws Exception {
        try (var handle = ctx.databaseManager().acquire(); Statement st = handle.connection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void firstRunSetupThenLoginRoundTrip() throws Exception {
        ctx.auth().ensureReady();
        assertTrue(ctx.auth().needsInitialSetup());
        AppUser admin = ctx.auth().createInitialAdministrator("first.admin", "Integration-Pass-1");
        assertEquals(Role.ADMIN, admin.role());
        assertFalse(ctx.auth().needsInitialSetup());

        assertNull(scalar("SELECT last_login_at FROM app_user WHERE username = 'first.admin'"));
        AppUser signedIn = ctx.auth().login("First.Admin", "Integration-Pass-1");
        assertEquals(admin.id(), signedIn.id());
        assertNotNull(scalar("SELECT last_login_at FROM app_user WHERE username = 'first.admin'"));
        assertTrue(ctx.session().isAuthenticated());

        String stored = scalar("SELECT password_hash FROM app_user WHERE username = 'first.admin'");
        assertTrue(stored.startsWith("pbkdf2_sha256$"));
        assertFalse(stored.contains("Integration-Pass-1"));
    }

    @Test
    void wrongPasswordUnknownUserAndInactiveAccountAreIndistinguishable() throws Exception {
        ctx.auth().createInitialAdministrator("boss", "Integration-Pass-1");
        ctx.auth().login("boss", "Integration-Pass-1");
        AppUser viewer = ctx.users().createUser("reader", "Pages-Pass-12345", Role.VIEWER);
        ctx.users().setActive(viewer.id(), false);
        ctx.auth().logout();

        String a = assertThrows(AuthenticationException.class, () -> ctx.auth().login("boss", "bad-password-1")).getMessage();
        String b = assertThrows(AuthenticationException.class, () -> ctx.auth().login("ghost", "bad-password-1")).getMessage();
        String c = assertThrows(AuthenticationException.class, () -> ctx.auth().login("reader", "Pages-Pass-12345")).getMessage();
        assertEquals(a, b);
        assertEquals(a, c);
        assertFalse(ctx.session().isAuthenticated());

        ctx.users(); // reactivation needs an admin session
        ctx.auth().login("boss", "Integration-Pass-1");
        ctx.users().setActive(viewer.id(), true);
        ctx.auth().logout();
        assertEquals("reader", ctx.auth().login("reader", "Pages-Pass-12345").username());
    }

    @Test
    void databaseEnforcesUniqueUsernameAndServiceReportsIt() throws Exception {
        ctx.auth().createInitialAdministrator("boss", "Integration-Pass-1");
        ctx.auth().login("boss", "Integration-Pass-1");
        ctx.users().createUser("dup.user", "Dup-Pass-1234567", Role.VIEWER);
        assertThrows(DuplicateEntityException.class, () -> ctx.users().createUser("DUP.USER", "Dup-Pass-1234567", Role.MANAGER));
    }

    @Test
    void lastActiveAdministratorIsProtectedAgainstLockout() throws Exception {
        AppUser boss = ctx.auth().createInitialAdministrator("boss", "Integration-Pass-1");
        ctx.auth().login("boss", "Integration-Pass-1");
        assertThrows(BusinessRuleException.class, () -> ctx.users().setActive(boss.id(), false));
        assertThrows(BusinessRuleException.class, () -> ctx.users().changeRole(boss.id(), Role.VIEWER));
        assertEquals("1", scalar("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN' AND is_active = 1"));
    }

    @Test
    void viewerSignedInCannotManageUsersOrWriteRecords() throws Exception {
        ctx.auth().createInitialAdministrator("boss", "Integration-Pass-1");
        ctx.auth().login("boss", "Integration-Pass-1");
        ctx.users().createUser("reader", "Pages-Pass-12345", Role.VIEWER);
        ctx.auth().logout();
        ctx.auth().login("reader", "Pages-Pass-12345");

        assertThrows(AuthorizationException.class, () -> ctx.users().listUsers());
        assertThrows(AuthorizationException.class, () -> ctx.users().createUser("evil", "Evil-Pass-1234567", Role.ADMIN));
        assertThrows(AuthorizationException.class, () -> ctx.owners().create(new com.estateflow.model.Owner("X Y", "9000000001", null, null)));
        assertEquals("0", scalar("SELECT COUNT(*) FROM owner"));
        assertDoesNotThrow(() -> ctx.owners().findAll());

        ctx.auth().logout();
        assertThrows(AuthorizationException.class, () -> ctx.owners().findAll());
    }

    @Test
    void passwordResetLetsTheNewPasswordInAndLocksTheOldOneOut() throws Exception {
        ctx.auth().createInitialAdministrator("boss", "Integration-Pass-1");
        ctx.auth().login("boss", "Integration-Pass-1");
        AppUser user = ctx.users().createUser("staff", "Crew-Pass-1234567", Role.MANAGER);
        ctx.users().resetPassword(user.id(), "Fresh-Pass-7654321");
        ctx.auth().logout();
        assertThrows(AuthenticationException.class, () -> ctx.auth().login("staff", "Crew-Pass-1234567"));
        assertEquals("staff", ctx.auth().login("staff", "Fresh-Pass-7654321").username());
    }
}
