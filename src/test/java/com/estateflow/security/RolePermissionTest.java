package com.estateflow.security;

import com.estateflow.exception.AuthorizationException;
import com.estateflow.model.enums.Role;
import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolePermissionTest {

    @Test
    void adminHasEveryPermission() {
        for (Permission p : Permission.values()) {
            assertTrue(Role.ADMIN.grants(p), p.name());
        }
    }

    @Test
    void managerRunsOperationsButCannotManageUsers() {
        assertTrue(Role.MANAGER.grants(Permission.MANAGE_RECORDS));
        assertTrue(Role.MANAGER.grants(Permission.RECORD_PAYMENTS));
        assertTrue(Role.MANAGER.grants(Permission.VIEW_REPORTS));
        assertFalse(Role.MANAGER.grants(Permission.MANAGE_USERS));
    }

    @Test
    void viewerIsReadOnly() {
        assertTrue(Role.VIEWER.grants(Permission.VIEW_DATA));
        assertTrue(Role.VIEWER.grants(Permission.VIEW_REPORTS));
        for (Permission p : new Permission[]{Permission.MANAGE_RECORDS, Permission.RECORD_PAYMENTS,
                Permission.MANAGE_SETTINGS, Permission.MANAGE_USERS, Permission.VIEW_AUDIT}) {
            assertFalse(Role.VIEWER.grants(p), p.name());
        }
    }

    @Test
    void authorizerUsesTheSessionsRoleAndDeniesWhenSignedOut() {
        Authorizer signedOut = TestAuth.signedOut();
        assertFalse(signedOut.can(Permission.VIEW_DATA));
        assertThrows(AuthorizationException.class, () -> signedOut.require(Permission.VIEW_DATA));
        assertThrows(AuthorizationException.class, signedOut::requireAuthenticated);

        Authorizer viewer = TestAuth.as(Role.VIEWER);
        assertDoesNotThrow(() -> viewer.require(Permission.VIEW_DATA));
        assertThrows(AuthorizationException.class, () -> viewer.require(Permission.MANAGE_RECORDS));
    }

    @Test
    void roleChangeInTheSessionTakesEffectImmediately() {
        Authorizer auth = TestAuth.as(Role.MANAGER);
        assertTrue(auth.can(Permission.MANAGE_RECORDS));
        auth.session().logout();
        assertFalse(auth.can(Permission.MANAGE_RECORDS));
    }
}
