package com.estateflow.service;

import com.estateflow.dao.TransactionManager;
import com.estateflow.dao.UserDao;
import com.estateflow.exception.*;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;
import com.estateflow.security.Authorizer;
import com.estateflow.security.PasswordHasher;
import com.estateflow.security.PasswordPolicy;
import com.estateflow.security.Permission;

import java.util.List;

/** Administrator-only account management. Every method requires {@link Permission#MANAGE_USERS}. */
public class UserManagementService {

    private final UserDao userDao;
    private final PasswordHasher hasher;
    private final TransactionManager tx;
    private final Authorizer auth;
    private final AuditService audit;

    public UserManagementService(UserDao userDao, PasswordHasher hasher, TransactionManager tx, Authorizer auth,
                                 AuditService audit) {
        this.audit = audit;
        this.userDao = userDao;
        this.hasher = hasher;
        this.tx = tx;
        this.auth = auth;
    }

    public List<AppUser> listUsers() {
        auth.require(Permission.MANAGE_USERS);
        return userDao.findAll();
    }

    public AppUser createUser(String username, String password, Role role) throws EstateFlowException {
        auth.require(Permission.MANAGE_USERS);
        if (role == null) {
            throw new ValidationException("Choose a role.");
        }
        String name = PasswordPolicy.normalizeUsername(username);
        PasswordPolicy.validatePassword(password, name);
        String hash = hasher.hash(password);
        try {
            return tx.inTransaction(() -> {
                AppUser created = userDao.insert(name, hash, role);
                audit.record("USER", created.id(), "CREATED", null,
                        AuditService.details("username", created.username(), "role", role.name()));
                return created;
            });
        } catch (DuplicateKeyException e) {
            throw new DuplicateEntityException("That username is already taken.", e);
        }
    }

    public void changeRole(long userId, Role role) throws EstateFlowException {
        AppUser actor = auth.require(Permission.MANAGE_USERS);
        if (role == null) {
            throw new ValidationException("Choose a role.");
        }
        tx.runInTransaction(() -> {
            AppUser target = find(userId);
            if (target.role() == role) {
                return;
            }
            if (target.role() == Role.ADMIN && target.active()) {
                requireAnotherActiveAdmin();
            }
            if (actor.id() != null && actor.id() == userId) {
                throw new BusinessRuleException("You cannot change your own role. Ask another administrator.", "AUTH-02");
            }
            userDao.updateRole(userId, role);
            audit.record("USER", userId, "ROLE_CHANGED", AuditService.details("username", target.username(), "role", target.role().name()),
                    AuditService.details("username", target.username(), "role", role.name()));
        });
    }

    public void setActive(long userId, boolean active) throws EstateFlowException {
        AppUser actor = auth.require(Permission.MANAGE_USERS);
        tx.runInTransaction(() -> {
            AppUser target = find(userId);
            if (target.active() == active) {
                return;
            }
            if (!active) {
                if (target.role() == Role.ADMIN) {
                    requireAnotherActiveAdmin();
                }
                if (actor.id() != null && actor.id() == userId) {
                    throw new BusinessRuleException("You cannot deactivate your own account. Ask another administrator.", "AUTH-02");
                }
            }
            userDao.updateActive(userId, active);
            audit.record("USER", userId, active ? "REACTIVATED" : "DEACTIVATED",
                    AuditService.details("username", target.username(), "active", target.active()),
                    AuditService.details("username", target.username(), "active", active));
        });
    }

    /** Replaces the password with a new one chosen by the administrator. The old password is never read or shown. */
    public void resetPassword(long userId, String newPassword) throws EstateFlowException {
        auth.require(Permission.MANAGE_USERS);
        AppUser target = find(userId);
        PasswordPolicy.validatePassword(newPassword, target.username());
        String hash = hasher.hash(newPassword);
        tx.runInTransaction(() -> {
            userDao.updatePasswordHash(userId, hash);
            // never any password material in the audit trail
            audit.record("USER", userId, "PASSWORD_RESET", null,
                    AuditService.details("username", target.username(), "note", "password replaced by an administrator"));
        });
    }

    private AppUser find(long userId) throws EntityNotFoundException {
        return userDao.findById(userId).orElseThrow(() -> new EntityNotFoundException("That user no longer exists."));
    }

    /** The target is an active administrator: at least one OTHER active administrator must remain. */
    private void requireAnotherActiveAdmin() throws BusinessRuleException {
        if (userDao.countActiveAdmins() <= 1) {
            throw new BusinessRuleException("At least one active administrator is required.", "AUTH-02");
        }
    }
}
