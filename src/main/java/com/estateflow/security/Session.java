package com.estateflow.security;

import com.estateflow.model.AppUser;

import java.util.Optional;

/** The authenticated user of this desktop instance (one user at a time). Cleared on logout. */
public final class Session {

    private volatile AppUser user;

    public void login(AppUser authenticated) {
        if (authenticated == null) {
            throw new IllegalArgumentException("user is required");
        }
        this.user = authenticated;
    }

    public void logout() {
        this.user = null;
    }

    public Optional<AppUser> currentUser() {
        return Optional.ofNullable(user);
    }

    public boolean isAuthenticated() {
        return user != null;
    }
}
