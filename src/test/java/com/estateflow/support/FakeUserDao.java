package com.estateflow.support;

import com.estateflow.dao.UserDao;
import com.estateflow.exception.DuplicateKeyException;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/** In-memory UserDao for unit tests; mirrors the unique-username rule of the real table. */
public class FakeUserDao implements UserDao {

    private final Map<Long, AppUser> users = new LinkedHashMap<>();
    private final Map<Long, String> hashes = new HashMap<>();
    private long nextId = 1;
    public boolean available = true;
    public int touchCount;

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Optional<Credentials> findCredentialsByUsername(String username) {
        return users.values().stream().filter(u -> u.username().equals(username)).findFirst()
                .map(u -> new Credentials(u, hashes.get(u.id())));
    }

    @Override
    public Optional<AppUser> findUserByUsername(String username) {
        return users.values().stream().filter(u -> u.username().equals(username)).findFirst();
    }

    @Override
    public Optional<AppUser> findById(long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public List<AppUser> findAll() {
        return new ArrayList<>(users.values());
    }

    @Override
    public AppUser insert(String username, String passwordHash, Role role) {
        if (users.values().stream().anyMatch(u -> u.username().equals(username))) {
            throw new DuplicateKeyException("duplicate", new SQLException("dup", "23000", 1062), "uq_app_user_username");
        }
        AppUser user = new AppUser(nextId++, username, role, true, LocalDateTime.of(2026, 1, 1, 0, 0), null);
        users.put(user.id(), user);
        hashes.put(user.id(), passwordHash);
        return user;
    }

    @Override
    public int updateRole(long userId, Role role) {
        return replace(userId, u -> new AppUser(u.id(), u.username(), role, u.active(), u.createdAt(), u.lastLoginAt()));
    }

    @Override
    public int updateActive(long userId, boolean active) {
        return replace(userId, u -> new AppUser(u.id(), u.username(), u.role(), active, u.createdAt(), u.lastLoginAt()));
    }

    @Override
    public int updatePasswordHash(long userId, String passwordHash) {
        if (!users.containsKey(userId)) {
            return 0;
        }
        hashes.put(userId, passwordHash);
        return 1;
    }

    @Override
    public void touchLastLogin(long userId, LocalDateTime utc) {
        touchCount++;
        replace(userId, u -> new AppUser(u.id(), u.username(), u.role(), u.active(), u.createdAt(), utc));
    }

    @Override
    public long countActiveAdmins() {
        return users.values().stream().filter(u -> u.role() == Role.ADMIN && u.active()).count();
    }

    public String storedHash(long userId) {
        return hashes.get(userId);
    }

    private int replace(long id, java.util.function.UnaryOperator<AppUser> change) {
        AppUser existing = users.get(id);
        if (existing == null) {
            return 0;
        }
        users.put(id, change.apply(existing));
        return 1;
    }
}
