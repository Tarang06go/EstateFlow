package com.estateflow.service;

import com.estateflow.dao.TransactionManager;
import com.estateflow.dao.UserDao;
import com.estateflow.exception.*;
import com.estateflow.model.AppUser;
import com.estateflow.model.enums.Role;
import com.estateflow.security.PasswordHasher;
import com.estateflow.security.PasswordPolicy;
import com.estateflow.security.Session;
import com.estateflow.service.FacialVerificationService.FaceProof;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

/** Sign-in, sign-out and first-administrator setup. Never logs or returns passwords or hashes. */
public class AuthService {

    static final String GENERIC_FAILURE = "Invalid username or password.";

    private final UserDao userDao;
    private final PasswordHasher hasher;
    private final Session session;
    private final TransactionManager tx;
    private final Clock clock;
    private final AuditService audit;
    private volatile String dummyHash;

    public AuthService(UserDao userDao, PasswordHasher hasher, Session session, TransactionManager tx, Clock clock,
                       AuditService audit) {
        this.audit = audit;
        this.userDao = userDao;
        this.hasher = hasher;
        this.session = session;
        this.tx = tx;
        this.clock = clock;
    }

    /** Fails with a clear setup message when the v1.2 migration has not been applied. */
    public void ensureReady() {
        if (!userDao.isAvailable()) {
            throw new ConfigurationException("The user table is missing. Apply db/migrations/v1.2_app_user.sql "
                    + "to the estateflow database (see README), then start EstateFlow again.");
        }
    }

    /** True when no active administrator exists, i.e. the first-run setup screen must be shown. */
    public boolean needsInitialSetup() {
        return userDao.countActiveAdmins() == 0;
    }

    /**
     * Signs in. Unknown username, wrong password and inactive account all give the same message, and the
     * same amount of hashing work. A failure leaves the session signed out.
     */
    public AppUser login(String username, String password) throws EstateFlowException {
        session.logout();
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || password == null || password.isEmpty()) {
            throw new AuthenticationException("Enter your username and password.");
        }
        Optional<UserDao.Credentials> found = userDao.findCredentialsByUsername(name);
        String stored = found.map(UserDao.Credentials::passwordHash).orElseGet(this::dummyHash);
        boolean passwordOk = hasher.verify(password, stored);
        if (found.isEmpty() || !passwordOk || !found.get().user().active()) {
            if (found.isPresent()) { // unknown usernames cannot be recorded (no entity); the screen message is identical either way
                audit.recordBestEffort("USER", found.get().user().id(), "SIGN_IN_FAILED", null,
                        AuditService.details("reason", passwordOk ? "account inactive" : "wrong password"));
            }
            throw new AuthenticationException(GENERIC_FAILURE);
        }
        AppUser user = found.get().user();
        userDao.touchLastLogin(user.id(), now());
        session.login(user);
        audit.recordBestEffort("USER", user.id(), "SIGNED_IN", null, null);
        return user;
    }

    /** Rechecks the signed-in account and its password before a sensitive operation. Does not replace the session. */
    public AppUser reauthenticateCurrent(String password) throws EstateFlowException {
        AppUser current = session.currentUser().filter(AppUser::active)
                .orElseThrow(() -> new AuthenticationException("Please sign in again."));
        Optional<UserDao.Credentials> credentials = userDao.findCredentialsByUsername(current.username());
        String stored = credentials.map(UserDao.Credentials::passwordHash).orElseGet(this::dummyHash);
        boolean valid = hasher.verify(password, stored);
        if (credentials.isEmpty() || !valid || !credentials.get().user().active()
                || !credentials.get().user().id().equals(current.id())) {
            throw new AuthenticationException("Your password could not be verified. No face enrollment was changed.");
        }
        return credentials.get().user();
    }

    /** Establishes the usual session only from a service-issued, expiring, single-use proof. */
    public AppUser loginWithFace(FaceProof proof) throws EstateFlowException {
        session.logout();
        long userId = proof == null ? -1L : proof.consume(clock.instant());
        Optional<AppUser> found = userId < 1 ? Optional.empty() : userDao.findById(userId);
        if (Thread.currentThread().isInterrupted() || found.isEmpty() || !found.get().active() || found.get().role() == Role.ADMIN) {
            if (found.isPresent()) audit.recordBestEffort("USER", userId, "SIGN_IN_FAILED", null,
                    AuditService.details("reason", "face account unavailable"));
            throw new AuthenticationException(GENERIC_FAILURE);
        }
        AppUser user = found.get();
        userDao.touchLastLogin(user.id(), now());
        Optional<AppUser> latest = Thread.currentThread().isInterrupted() ? Optional.empty() : userDao.findById(user.id());
        if(latest.isEmpty() || !latest.get().active() || latest.get().role() == Role.ADMIN
                || Thread.currentThread().isInterrupted()) throw new AuthenticationException(GENERIC_FAILURE);
        session.login(latest.get());
        audit.recordBestEffort("USER", user.id(), "SIGNED_IN", null, AuditService.details("method", "FACE"));
        return latest.get();
    }

    public void logout() {
        session.currentUser().ifPresent(u -> audit.recordBestEffort("USER", u.id(), "SIGNED_OUT", null, null));
        session.logout();
    }

    /** Creates the first administrator; refused as soon as an active administrator exists. */
    public AppUser createInitialAdministrator(String username, String password) throws EstateFlowException {
        String name = PasswordPolicy.normalizeUsername(username);
        PasswordPolicy.validatePassword(password, name);
        String hash = hasher.hash(password);
        try {
            return tx.inTransaction(() -> {
                if (userDao.countActiveAdmins() > 0) {
                    throw new BusinessRuleException("An administrator already exists. Sign in instead.", "AUTH-01");
                }
                AppUser created = userDao.insert(name, hash, Role.ADMIN);
                audit.record("USER", created.id(), "CREATED", null,
                        AuditService.details("username", created.username(), "role", "ADMIN", "via", "first-run setup"));
                return created;
            });
        } catch (DuplicateKeyException e) {
            throw new DuplicateEntityException("That username is already taken.", e);
        }
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = hasher.hash("not-a-real-password-" + System.nanoTime());
            dummyHash = hash;
        }
        return hash;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
