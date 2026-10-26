package com.estateflow.service;

import com.estateflow.exception.*;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;
import com.estateflow.security.Authorizer;
import com.estateflow.security.PasswordHasher;
import com.estateflow.security.Session;
import com.estateflow.support.FakeUserDao;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserManagementServiceTest {

    private final PasswordHasher hasher = new PasswordHasher(2_000);
    private FakeUserDao dao;
    private AppUser admin;
    private UserManagementService service;

    private UserManagementService serviceAs(Authorizer auth) {
        return new UserManagementService(dao, hasher, new PassThroughTransactionManager(), auth, AuditService.noop());
    }

    private Authorizer signedInAs(AppUser user) {
        Session s = new Session();
        s.login(user);
        return new Authorizer(s);
    }

    @BeforeEach
    void setUp() {
        dao = new FakeUserDao();
        admin = dao.insert("admin", hasher.hash("Admin-Pass-2026"), Role.ADMIN);
        service = serviceAs(signedInAs(admin));
    }

    @Test
    void nonAdministratorsAreDeniedEveryUserManagementOperation() {
        for (Authorizer denied : new Authorizer[]{TestAuth.as(Role.MANAGER), TestAuth.as(Role.VIEWER), TestAuth.signedOut()}) {
            UserManagementService s = serviceAs(denied);
            assertThrows(AuthorizationException.class, s::listUsers);
            assertThrows(AuthorizationException.class, () -> s.createUser("newuser", "New-Pass-12345", Role.VIEWER));
            assertThrows(AuthorizationException.class, () -> s.changeRole(admin.id(), Role.VIEWER));
            assertThrows(AuthorizationException.class, () -> s.setActive(admin.id(), false));
            assertThrows(AuthorizationException.class, () -> s.resetPassword(admin.id(), "Reset-Pass-12345"));
        }
        assertEquals(1, dao.findAll().size(), "nothing was created or changed");
    }

    @Test
    void managerCannotEscalateThemselvesToAdmin() throws Exception {
        AppUser manager = dao.insert("manager1", hasher.hash("Manager-Pass-1"), Role.MANAGER);
        UserManagementService asManager = serviceAs(signedInAs(manager));
        assertThrows(AuthorizationException.class, () -> asManager.changeRole(manager.id(), Role.ADMIN));
        assertEquals(Role.MANAGER, dao.findById(manager.id()).orElseThrow().role());
    }

    @Test
    void adminCreatesUsersWithHashedPasswords() throws Exception {
        AppUser created = service.createUser("  New.User ", "New-Pass-12345", Role.VIEWER);
        assertEquals("new.user", created.username());
        assertEquals(Role.VIEWER, created.role());
        assertTrue(created.active());
        assertNotEquals("New-Pass-12345", dao.storedHash(created.id()));
        assertTrue(hasher.verify("New-Pass-12345", dao.storedHash(created.id())));
    }

    @Test
    void duplicateUsernameIsRejectedCaseInsensitively() throws Exception {
        service.createUser("sam", "Quick-Pass-123456", Role.VIEWER);
        assertThrows(DuplicateEntityException.class, () -> service.createUser("SAM", "Other-Pass-12345", Role.MANAGER));
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(ValidationException.class, () -> service.createUser("x", "New-Pass-12345", Role.VIEWER));
        assertThrows(ValidationException.class, () -> service.createUser("validname", "weak", Role.VIEWER));
        assertThrows(ValidationException.class, () -> service.createUser("validname", "New-Pass-12345", null));
    }

    @Test
    void theOnlyActiveAdministratorCannotBeDeactivatedOrDemoted() {
        assertThrows(BusinessRuleException.class, () -> service.setActive(admin.id(), false));
        assertThrows(BusinessRuleException.class, () -> service.changeRole(admin.id(), Role.MANAGER));
        assertTrue(dao.findById(admin.id()).orElseThrow().active());
        assertEquals(Role.ADMIN, dao.findById(admin.id()).orElseThrow().role());
    }

    @Test
    void lastAdminRuleHoldsEvenWhenActedOnByAStaleSession() throws Exception {
        AppUser other = dao.insert("other.admin", hasher.hash("Other-Pass-12345"), Role.ADMIN);
        service.setActive(other.id(), false); // fine: "admin" remains
        // the remaining admin is the only one again; a different actor must still not remove it
        UserManagementService stale = serviceAs(signedInAs(other));
        assertThrows(BusinessRuleException.class, () -> stale.setActive(admin.id(), false));
        assertThrows(BusinessRuleException.class, () -> stale.changeRole(admin.id(), Role.VIEWER));
    }

    @Test
    void withTwoAdminsOneCanBeDeactivatedButNotSelf() throws Exception {
        AppUser other = dao.insert("other.admin", hasher.hash("Other-Pass-12345"), Role.ADMIN);
        assertThrows(BusinessRuleException.class, () -> service.setActive(admin.id(), false), "self-deactivation");
        service.setActive(other.id(), false);
        assertFalse(dao.findById(other.id()).orElseThrow().active());
        service.setActive(other.id(), true);
        assertTrue(dao.findById(other.id()).orElseThrow().active());
    }

    @Test
    void adminCanChangeAnotherUsersRoleButNotTheirOwn() throws Exception {
        AppUser other = dao.insert("other.admin", hasher.hash("Other-Pass-12345"), Role.ADMIN);
        AppUser viewer = service.createUser("viewer1", "Viewer-Pass-1234", Role.VIEWER);
        service.changeRole(viewer.id(), Role.MANAGER);
        assertEquals(Role.MANAGER, dao.findById(viewer.id()).orElseThrow().role());
        assertThrows(BusinessRuleException.class, () -> service.changeRole(admin.id(), Role.VIEWER));
        service.changeRole(other.id(), Role.VIEWER);
        assertEquals(1, dao.countActiveAdmins());
    }

    @Test
    void resetPasswordReplacesTheHashAndTheOldPasswordStopsWorking() throws Exception {
        AppUser user = service.createUser("resetme", "Old-Pass-123456", Role.VIEWER);
        String oldHash = dao.storedHash(user.id());
        service.resetPassword(user.id(), "Brand-New-Pass-77");
        String newHash = dao.storedHash(user.id());
        assertNotEquals(oldHash, newHash);
        assertTrue(hasher.verify("Brand-New-Pass-77", newHash));
        assertFalse(hasher.verify("Old-Pass-123456", newHash));
        assertThrows(ValidationException.class, () -> service.resetPassword(user.id(), "weak"));
        assertThrows(EntityNotFoundException.class, () -> service.resetPassword(9999, "Brand-New-Pass-77"));
    }

    @Test
    void listedUsersCarryNoPasswordHash() throws Exception {
        service.createUser("someone", "Some-Pass-123456", Role.VIEWER);
        for (AppUser u : service.listUsers()) {
            assertFalse(u.toString().contains("pbkdf2"), "AppUser must not expose hashes");
        }
    }
}
