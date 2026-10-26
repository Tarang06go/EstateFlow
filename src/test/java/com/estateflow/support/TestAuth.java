package com.estateflow.support;

import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Session;

import java.time.LocalDateTime;

/** Authorizers for tests: a session already signed in with the given role. */
public final class TestAuth {

    private TestAuth() {
    }

    public static Authorizer as(Role role) {
        Session session = new Session();
        session.login(new AppUser(1L, role.name().toLowerCase() + "-test", role, true, LocalDateTime.of(2026, 1, 1, 0, 0), null));
        return new Authorizer(session);
    }

    public static Authorizer admin() {
        return as(Role.ADMIN);
    }

    public static Authorizer signedOut() {
        return new Authorizer(new Session());
    }
}
