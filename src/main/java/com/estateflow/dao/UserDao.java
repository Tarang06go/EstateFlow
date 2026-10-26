package com.estateflow.dao;

import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence contract for application accounts. Password hashes only travel through {@link Credentials}. */
public interface UserDao {

    /** A user together with the stored hash; used only by the sign-in path. */
    record Credentials(AppUser user, String passwordHash) {
    }

    /** True when the app_user table exists (the v1.2 migration has been applied). */
    boolean isAvailable();

    Optional<Credentials> findCredentialsByUsername(String username);

    /** Public account fields only; biometric sign-in must not receive a password hash. */
    Optional<AppUser> findUserByUsername(String username);

    Optional<AppUser> findById(long userId);

    List<AppUser> findAll();

    /** Inserts and returns the user with its generated id. Throws DuplicateKeyException for a duplicate username. */
    AppUser insert(String username, String passwordHash, Role role);

    int updateRole(long userId, Role role);

    int updateActive(long userId, boolean active);

    int updatePasswordHash(long userId, String passwordHash);

    void touchLastLogin(long userId, LocalDateTime utc);

    long countActiveAdmins();
}
