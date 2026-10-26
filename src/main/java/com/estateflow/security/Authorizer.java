package com.estateflow.security;

import com.estateflow.exception.AuthorizationException;
import com.estateflow.model.AppUser;

/** Checks the current session's role. Services call this first, so rules hold even when the UI is bypassed. */
public class Authorizer {

    private final Session session;

    public Authorizer(Session session) {
        this.session = session;
    }

    public Session session() {
        return session;
    }

    public boolean can(Permission permission) {
        return session.currentUser().map(u -> u.active() && u.role().grants(permission)).orElse(false);
    }

    /** Returns the signed-in user or throws {@link AuthorizationException}. */
    public AppUser requireAuthenticated() {
        return session.currentUser().filter(AppUser::active)
                .orElseThrow(() -> new AuthorizationException("Please sign in to continue."));
    }

    public AppUser require(Permission permission) {
        AppUser user = requireAuthenticated();
        if (!user.role().grants(permission)) {
            throw new AuthorizationException("Your role does not permit this action.");
        }
        return user;
    }
}
