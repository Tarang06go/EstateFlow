package com.estateflow.service;

import com.estateflow.dao.BiometricDao;
import com.estateflow.dao.UserDao;
import com.estateflow.exception.AuthenticationException;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.exception.EstateFlowException;
import com.estateflow.model.AppUser;
import com.estateflow.model.FaceEnrollmentRecord;
import com.estateflow.model.enums.Role;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.service.biometric.*;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional per-user/per-Windows-profile face matching. It never creates a session; AuthService consumes its one-use proof. */
public final class FacialVerificationService implements AutoCloseable {
    public static final String CONSENT_VERSION = "face-consent-v1";
    private static final int ENROLL_SAMPLES = 5, VERIFY_SAMPLES = 3;
    private static final double CONSISTENCY_THRESHOLD = .363;
    private static final Duration FRAME_TIMEOUT = Duration.ofSeconds(5);
    private static final String GENERIC_FAILURE = "Face verification could not be completed. Use your password to sign in.";

    public record Status(boolean providerAvailable, boolean enabled, boolean enrolled, boolean consented, String message) { }

    private final CameraProvider camera;
    private final FaceEngine engine;
    private final FaceModelFiles models;
    private final BiometricDao biometricDao;
    private final UserDao userDao;
    private final AesGcmFaceTemplateCipher cipher;
    private final TemplateKeyProvider keyProvider;
    private final AuthService auth;
    private final Authorizer authorizer;
    private final AuditService audit;
    private final Clock clock;
    private final FaceAttemptLimiter limiter;
    private final double matchThreshold;
    private final Duration cameraOpenTimeout;
    private final boolean loginEnabled;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.Set<AtomicBoolean> active = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public FacialVerificationService(CameraProvider camera, FaceEngine engine, FaceModelFiles models,
                                     BiometricDao biometricDao, UserDao userDao, AesGcmFaceTemplateCipher cipher,
                                     TemplateKeyProvider keyProvider, AuthService auth, Authorizer authorizer,
                                     AuditService audit, Clock clock, double matchThreshold, Duration cameraOpenTimeout) {
        this(camera,engine,models,biometricDao,userDao,cipher,keyProvider,auth,authorizer,audit,clock,
                matchThreshold,cameraOpenTimeout,false);
    }

    public FacialVerificationService(CameraProvider camera, FaceEngine engine, FaceModelFiles models,
                                     BiometricDao biometricDao, UserDao userDao, AesGcmFaceTemplateCipher cipher,
                                     TemplateKeyProvider keyProvider, AuthService auth, Authorizer authorizer,
                                     AuditService audit, Clock clock, double matchThreshold, Duration cameraOpenTimeout,
                                     boolean loginEnabled) {
        this.camera=camera; this.engine=engine; this.models=models; this.biometricDao=biometricDao; this.userDao=userDao;
        this.cipher=cipher; this.keyProvider=keyProvider; this.auth=auth; this.authorizer=authorizer; this.audit=audit;
        this.clock=clock; this.limiter=new FaceAttemptLimiter(clock); this.matchThreshold=matchThreshold;
        this.cameraOpenTimeout=cameraOpenTimeout;
        this.loginEnabled=loginEnabled;
        if(!Double.isFinite(matchThreshold) || matchThreshold < .363 || matchThreshold > .90)
            throw new IllegalArgumentException("Face matching threshold must be between the experimental reference 0.363 and 0.90.");
    }

    /** True only when the local model files and native runtime load; camera access is deferred until user action. */
    public boolean providerAvailable() {
        try {
            return models.available() && engine.available() && camera.available();
        } catch(RuntimeException unavailable) { return false; }
    }

    /** Login-screen readiness intentionally excludes username/enrollment state to avoid account enumeration. */
    public boolean loginProviderAvailable() { return loginEnabled && providerAvailable(); }

    public Status status() {
        AppUser user=authorizer.requireAuthenticated();
        Optional<FaceEnrollmentRecord> enrollment=biometricDao.find(user.id(),keyProvider.deviceId());
        boolean available=models.available() && engine.available() && camera.available();
        boolean enrolled=enrollment.isPresent();
        boolean consented=enrollment.map(e->CONSENT_VERSION.equals(e.consentVersion())).orElse(false);
        boolean enabled=loginEnabled && enrollment.map(FaceEnrollmentRecord::enabled).orElse(false)&&consented;
        String message = !loginEnabled ? "Face sign-in is disabled by the application safety setting. Password sign-in remains available."
                    : !available ? "Face matching is unavailable on this computer. Password sign-in remains available."
                    : enrolled ? (enabled ? "Face sign-in is enabled on this Windows user profile." : "An enrollment exists on this computer but face sign-in is disabled.")
                : "No face enrollment exists on this computer.";
        return new Status(available,enabled,enrolled,consented,message);
    }

    public void enroll(String password, boolean consent, CancellationSignal cancelled) throws EstateFlowException {
        AppUser user=authorizer.require(Permission.MANAGE_OWN_FACE_PROFILE);
        ensureEligible(user);
        if (!consent) throw new AuthorizationException("Explicit consent is required before enrollment.");
        reauthenticate(user,password);
        if(closed.get()) throw new IllegalStateException("Face verification is shutting down.");
        AtomicBoolean operationCancel=new AtomicBoolean(); active.add(operationCancel);
        FaceEnrollmentException.Stage stage=FaceEnrollmentException.Stage.PROVIDER_CHECK;
        List<FaceEmbedding> samples=null;
        FaceEmbedding average=null;
        try {
            checkEnrollmentCancelled(cancelled);
            if(!models.available() || !engine.available() || !camera.available()) throw new IllegalStateException("Face provider unavailable.");

            stage=FaceEnrollmentException.Stage.CAPTURE_AND_FACE_PROCESSING;
            samples=capture(ENROLL_SAMPLES,()->operationCancel.get() || closed.get() || cancelled.cancelled());
            checkEnrollmentCancelled(cancelled);

            stage=FaceEnrollmentException.Stage.SAMPLE_CONSISTENCY;
            average=FaceEmbedding.average(samples,CONSISTENCY_THRESHOLD);
            checkEnrollmentCancelled(cancelled);

            stage=FaceEnrollmentException.Stage.PASSWORD_REAUTHENTICATION;
            reauthenticate(user,password);
            checkEnrollmentCancelled(cancelled);

            stage=FaceEnrollmentException.Stage.LOCAL_KEY_AND_ENCRYPTION;
            EncryptedFaceTemplate encrypted=cipher.encrypt(user.id(),average);
            checkEnrollmentCancelled(cancelled);
            FaceEnrollmentRecord record=new FaceEnrollmentRecord(user.id(),keyProvider.deviceId(),encrypted.ciphertext(),encrypted.nonce(),encrypted.keyVersion(),encrypted.templateVersion(),encrypted.modelVersion(),CONSENT_VERSION,LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),true);

            stage=FaceEnrollmentException.Stage.DATABASE_PERSISTENCE;
            if(!cancelled.beginCommit() || cancelled.cancelled() || closed.get() || Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Face enrollment was cancelled before persistence.");
            biometricDao.save(record);
            audit.recordBestEffort("USER",user.id(),"FACE_ENROLLMENT_COMPLETED",null,AuditService.details("device",keyProvider.deviceId(),"consentVersion",CONSENT_VERSION));
        } catch(FaceEnrollmentException e) {
            throw e;
        } catch(Exception e) {
            if(cancelled.cancelled() || closed.get() || Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Face enrollment was cancelled.");
            throw FaceEnrollmentException.at(stage,e);
        } finally {
            if(samples!=null) close(samples);
            if(average!=null) average.close();
            active.remove(operationCancel);
        }
    }

    private void checkEnrollmentCancelled(CancellationSignal cancellation) {
        if(cancellation.cancelled() || closed.get() || Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Face enrollment was cancelled.");
    }

    public void setEnabled(boolean enabled, String password) throws EstateFlowException {
        AppUser user=authorizer.require(Permission.MANAGE_OWN_FACE_PROFILE); ensureEligible(user); reauthenticate(user,password);
        if(enabled) {
            FaceEnrollmentRecord record=biometricDao.find(user.id(),keyProvider.deviceId()).orElseThrow(()->new IllegalStateException("Enroll on this computer before enabling face sign-in."));
            if(!CONSENT_VERSION.equals(record.consentVersion())) throw new IllegalStateException("Consent must be renewed before enabling face sign-in.");
            if(!keyProvider.loadExistingKey(user.id()).isPresent()) throw new IllegalStateException("The local face key is unavailable; revoke and enroll again on this computer.");
        }
        if(biometricDao.setEnabled(user.id(),keyProvider.deviceId(),enabled)==0) throw new IllegalStateException("No face enrollment exists on this computer.");
        audit.recordBestEffort("USER",user.id(),enabled?"FACE_LOGIN_ENABLED":"FACE_LOGIN_DISABLED",null,AuditService.details("device",keyProvider.deviceId()));
    }

    public void revokeAndDelete(String password) throws EstateFlowException {
        AppUser user=authorizer.require(Permission.MANAGE_OWN_FACE_PROFILE); ensureEligible(user); reauthenticate(user,password);
        biometricDao.deleteAllForUser(user.id());
        keyProvider.deleteKey(user.id());
        audit.recordBestEffort("USER",user.id(),"FACE_CONSENT_REVOKED",null,null);
    }

    public FaceProof verifyForLogin(String username, CancellationSignal cancelled) throws AuthenticationException {
        auth.logout();
        String name=username==null?"":username.trim().toLowerCase(Locale.ROOT);
        if(!loginEnabled || closed.get() || name.isBlank() || name.length()>50 || !limiter.mayAttempt(name)) throw new AuthenticationException(GENERIC_FAILURE);
        AppUser user=userDao.findUserByUsername(name).orElse(null);
        if(user==null || !user.active() || user.role()==Role.ADMIN) { limiter.failure(name); throw new AuthenticationException(GENERIC_FAILURE); }
        AtomicBoolean cancellation=new AtomicBoolean(); active.add(cancellation);
        try {
            if(cancelled.cancelled()) { cancellation.set(true); throw new AuthenticationException(GENERIC_FAILURE); }
            FaceEnrollmentRecord record=biometricDao.find(user.id(),keyProvider.deviceId())
                    .filter(e->e.enabled()&&CONSENT_VERSION.equals(e.consentVersion())).orElseThrow(()->new IllegalStateException("Enrollment unavailable."));
            if(closed.get() || !models.available() || !engine.available() || !camera.available()) throw new IllegalStateException("Provider unavailable.");
            FaceEmbedding expected=cipher.decrypt(user.id(),new EncryptedFaceTemplate(record.keyVersion(),record.templateVersion(),record.modelVersion(),record.nonce(),record.ciphertext()));
            List<FaceEmbedding> samples=null;
            try {
                samples=capture(VERIFY_SAMPLES,()->cancellation.get() || cancelled.cancelled());
                FaceEmbedding observed=FaceEmbedding.average(samples,CONSISTENCY_THRESHOLD);
                try {
                    if(observed.similarity(expected)<matchThreshold || cancelled.cancelled() || closed.get()) throw new IllegalStateException("No match.");
                } finally { observed.close(); }
            } finally { expected.close(); if(samples!=null)close(samples); }
            if(cancelled.cancelled()) throw new IllegalStateException("Cancelled.");
            limiter.success(name);
            audit.recordBestEffort("USER",user.id(),"FACE_VERIFICATION_SUCCEEDED",null,AuditService.details("device",keyProvider.deviceId()));
            if(cancelled.cancelled() || Thread.currentThread().isInterrupted()) throw new IllegalStateException("Cancelled.");
            return new FaceProof(user.id(),clock.instant().plusSeconds(30),closed,cancelled);
        } catch(AuthenticationException e) { throw e; }
        catch(Exception e) {
            if(cancelled.cancelled() || closed.get() || Thread.currentThread().isInterrupted()) throw new AuthenticationException(GENERIC_FAILURE);
            limiter.failure(name);
            audit.recordBestEffort("USER",user.id(),"FACE_VERIFICATION_FAILED",null,AuditService.details("reason","verification unavailable or not matched"));
            throw new AuthenticationException(GENERIC_FAILURE);
        } finally { active.remove(cancellation); }
    }

    private List<FaceEmbedding> capture(int count,CancellationSignal cancellation) throws CameraUnavailableException,FaceProcessingException {
        if(closed.get() || cancellation.cancelled()) throw new CameraUnavailableException("Camera operation cancelled.");
        List<FaceEmbedding> samples=new ArrayList<>();
        try(CameraProvider.CaptureSession session=camera.open(cameraOpenTimeout,cancellation)) {
            for(int i=0;i<count;i++) {
                if(cancellation.cancelled()) throw new CameraUnavailableException("Camera operation cancelled.");
                try(CameraFrame frame=session.capture(FRAME_TIMEOUT,cancellation)) { samples.add(engine.extract(frame)); }
            }
            return samples;
        } catch(CameraUnavailableException|FaceProcessingException|RuntimeException e) { close(samples); throw e; }
        catch(Exception e) { close(samples); throw new CameraUnavailableException("Camera resources could not be released.",e); }
    }
    private void ensureEligible(AppUser user) throws AuthorizationException {
        if(user.role()==Role.ADMIN) throw new AuthorizationException("Administrator accounts use password sign-in only.");
    }
    private void reauthenticate(AppUser expected,String password) throws EstateFlowException {
        AppUser checked=auth.reauthenticateCurrent(password); ensureEligible(checked);
        if(!expected.id().equals(checked.id())) throw new AuthenticationException("Your account changed during the face operation. No change was saved.");
    }
    private static void close(List<FaceEmbedding> samples) { for(FaceEmbedding sample:samples) sample.close(); }
    @Override public void close() { closed.set(true); active.forEach(flag->flag.set(true)); engine.close(); }

    /** Opaque proof. Only this service can construct one; a proof expires after 30 seconds and is single-use. */
    public static final class FaceProof {
        private final long userId;
        private final java.time.Instant expiresAt;
        private final AtomicBoolean consumed=new AtomicBoolean();
        private final AtomicBoolean serviceClosed;
        private final CancellationSignal cancellation;
        private FaceProof(long userId,java.time.Instant expiresAt,AtomicBoolean serviceClosed,CancellationSignal cancellation) { this.userId=userId; this.expiresAt=expiresAt; this.serviceClosed=serviceClosed; this.cancellation=cancellation; }
        long consume(java.time.Instant now) {
            if(!consumed.compareAndSet(false,true) || serviceClosed.get() || cancellation.cancelled()
                    || Thread.currentThread().isInterrupted() || !now.isBefore(expiresAt)) return -1L;
            return userId;
        }
        @Override public String toString() { return "FaceProof[opaque]"; }
    }
}
