package com.estateflow.service;

import com.estateflow.dao.BiometricDao;
import com.estateflow.exception.AuthenticationException;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.model.AppUser;
import com.estateflow.model.FaceEnrollmentRecord;
import com.estateflow.model.enums.Role;
import com.estateflow.security.Authorizer;
import com.estateflow.security.PasswordHasher;
import com.estateflow.security.Session;
import com.estateflow.service.biometric.*;
import com.estateflow.support.FakeUserDao;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FacialVerificationServiceTest {
    private static final String PASSWORD="Valid-Pass-123";

    @Test void consentAndPasswordReauthenticationAreRequiredBeforeCapture() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login();
        assertThrows(AuthorizationException.class,()->f.service.enroll(PASSWORD,false,()->false));
        assertThrows(AuthenticationException.class,()->f.service.enroll("wrong-password",true,()->false));
        assertEquals(0,f.camera.captures);
        f.service.enroll(PASSWORD,true,()->false);
        assertEquals(5,f.camera.captures);
        assertTrue(f.repository.find(f.user.id(),f.keys.deviceId()).orElseThrow().enabled());
    }

    @Test void faceProofCreatesNormalSessionAndIsSingleUse() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login(); f.service.enroll(PASSWORD,true,()->false); f.auth.logout();
        var proof=f.service.verifyForLogin("manager",()->false);
        AppUser loggedIn=f.auth.loginWithFace(proof);
        assertEquals(f.user.id(),loggedIn.id()); assertTrue(f.session.isAuthenticated()); assertEquals(2,f.users.touchCount);
        f.auth.logout();
        assertThrows(AuthenticationException.class,()->f.auth.loginWithFace(proof));
        assertFalse(f.session.isAuthenticated());
    }

    @Test void expiredOrShutdownProofsCannotCreateASession() throws Exception {
        Fixture expired=new Fixture(Role.MANAGER); expired.login(); expired.service.enroll(PASSWORD,true,()->false); expired.auth.logout();
        var expiredProof=expired.service.verifyForLogin("manager",()->false);
        expired.clock.advanceSeconds(31);
        assertThrows(AuthenticationException.class,()->expired.auth.loginWithFace(expiredProof));
        assertFalse(expired.session.isAuthenticated());

        Fixture shutdown=new Fixture(Role.MANAGER); shutdown.login(); shutdown.service.enroll(PASSWORD,true,()->false); shutdown.auth.logout();
        var shutdownProof=shutdown.service.verifyForLogin("manager",()->false);
        shutdown.service.close();
        assertThrows(AuthenticationException.class,()->shutdown.auth.loginWithFace(shutdownProof));
        assertFalse(shutdown.session.isAuthenticated());
    }

    @Test void cancelledProofCannotFinishLoginAndGlobalGateDefaultsOff() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login(); f.service.enroll(PASSWORD,true,()->false); f.auth.logout();
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        var proof=f.service.verifyForLogin("manager",cancelled::get);
        cancelled.set(true);
        assertThrows(AuthenticationException.class,()->f.auth.loginWithFace(proof));
        assertFalse(f.session.isAuthenticated());

        Fixture disabled=new Fixture(Role.MANAGER,false); disabled.login();
        assertFalse(disabled.service.loginProviderAvailable());
        assertThrows(AuthenticationException.class,()->disabled.service.verifyForLogin("manager",()->false));
        assertFalse(disabled.session.isAuthenticated());
    }

    @Test void globallyDisabledGateIsSeparateFromAnEnabledConsentedEnrollment() throws Exception {
        Fixture f=new Fixture(Role.MANAGER,false); f.login();
        f.service.enroll(PASSWORD,true,()->false);
        FaceEnrollmentRecord enrollment=f.repository.find(f.user.id(),f.keys.deviceId()).orElseThrow();
        assertTrue(enrollment.enabled());
        assertEquals(FacialVerificationService.CONSENT_VERSION,enrollment.consentVersion());

        FacialVerificationService.Status status=f.service.status();
        assertTrue(status.enrolled());
        assertTrue(status.consented());
        assertFalse(status.enabled());
        assertTrue(status.message().contains("application safety setting"));
        assertFalse(f.service.loginProviderAvailable());
        int capturesBeforeLogin=f.camera.captures;
        assertThrows(AuthenticationException.class,()->f.service.verifyForLogin("manager",()->false));
        assertEquals(capturesBeforeLogin,f.camera.captures,"The global gate must reject before opening the camera.");
        assertFalse(f.session.isAuthenticated());
        f.auth.login("manager",PASSWORD);
        assertTrue(f.session.isAuthenticated(),"Password sign-in remains available when face login is gated off.");
    }

    @Test void enrollmentOnAnotherWindowsProfileIsNotUsedForFaceLogin() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login(); f.service.enroll(PASSWORD,true,()->false); f.auth.logout();
        int capturesAfterEnrollment=f.camera.captures;
        f.keys.device="00000000-0000-4000-8000-000000000002";
        assertThrows(AuthenticationException.class,()->f.service.verifyForLogin("manager",()->false));
        assertEquals(capturesAfterEnrollment,f.camera.captures,"A record from another device must not be used for matching.");
        assertFalse(f.session.isAuthenticated());
    }

    @Test void failedVerificationClearsAnyPriorSession() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login(); assertTrue(f.session.isAuthenticated());
        assertThrows(AuthenticationException.class,()->f.service.verifyForLogin("manager",()->false));
        assertFalse(f.session.isAuthenticated());
    }

    @Test void wrongFaceNeverCreatesProofOrSessionAndFaceThrottleDoesNotBlockPassword() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login(); f.service.enroll(PASSWORD,true,()->false); f.auth.logout();
        f.engine.vector=vector(1);
        for(int i=0;i<5;i++) assertThrows(AuthenticationException.class,()->f.service.verifyForLogin("manager",()->false));
        assertThrows(AuthenticationException.class,()->f.service.verifyForLogin("manager",()->false));
        assertFalse(f.session.isAuthenticated());
        f.auth.login("manager",PASSWORD);
        assertTrue(f.session.isAuthenticated());
    }

    @Test void deactivatedAccountIsRejectedBothBeforeAndAfterMatching() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login(); f.service.enroll(PASSWORD,true,()->false); f.auth.logout();
        var proof=f.service.verifyForLogin("manager",()->false);
        f.users.updateActive(f.user.id(),false);
        assertThrows(AuthenticationException.class,()->f.auth.loginWithFace(proof));
        assertFalse(f.session.isAuthenticated());
        assertThrows(AuthenticationException.class,()->f.service.verifyForLogin("manager",()->false));
        assertFalse(f.session.isAuthenticated());
    }

    @Test void adminAccountsAreExcludedAndEnrollmentCannotChangeAnotherProfile() throws Exception {
        Fixture f=new Fixture(Role.ADMIN); f.login();
        assertThrows(AuthorizationException.class,()->f.service.enroll(PASSWORD,true,()->false));
        f.auth.logout();
        assertThrows(AuthenticationException.class,()->f.service.verifyForLogin("manager",()->false));
        assertTrue(f.repository.records.isEmpty());
    }

    @Test void disablingAndRevokingRemoveFaceAccessAndEnrollment() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login(); f.service.enroll(PASSWORD,true,()->false);
        f.service.setEnabled(false,PASSWORD);
        f.auth.logout();
        assertThrows(AuthenticationException.class,()->f.service.verifyForLogin("manager",()->false));
        f.auth.login("manager",PASSWORD);
        f.service.revokeAndDelete(PASSWORD);
        assertTrue(f.repository.records.isEmpty());
    }

    @Test void cancellationDuringCaptureDoesNotLeaveAnEnrollment() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login();
        assertThrows(java.util.concurrent.CancellationException.class,()->f.service.enroll(PASSWORD,true,()->true));
        assertTrue(f.repository.records.isEmpty());
        assertEquals(0,f.camera.activeSessions);
    }

    @Test void cancellationAfterTheFifthFrameCannotPersistAnEnrollment() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login();
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        f.camera.afterCapture=()->{ if(f.camera.captures==5) cancelled.set(true); };
        assertThrows(java.util.concurrent.CancellationException.class,()->f.service.enroll(PASSWORD,true,cancelled::get));
        assertTrue(f.repository.records.isEmpty());
        assertEquals(0,f.camera.activeSessions);
    }

    @Test void enrollmentFailureReportsStageAndPreservesCauseWithoutLeakingSamples() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login();
        FaceProcessingException rootCause=new FaceProcessingException("synthetic provider failure");
        f.engine.failure=rootCause;
        FaceEnrollmentException failure=assertThrows(FaceEnrollmentException.class,
                ()->f.service.enroll(PASSWORD,true,()->false));
        assertEquals(FaceEnrollmentException.Stage.CAPTURE_AND_FACE_PROCESSING,failure.stage());
        assertSame(rootCause,failure.getCause());
        assertTrue(f.repository.records.isEmpty());
        assertEquals(0,f.camera.activeSessions);
        try(FaceEmbedding sample=new FaceEmbedding(vector(0))) {
            assertEquals("FaceEmbedding[redacted]",sample.toString());
        }
    }

    @Test void zeroFaceEnrollmentFailureIsActionableAndSavesNoPartialRecord() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login();
        f.engine.failure=new FaceProcessingException(FaceProcessingException.Reason.NO_FACE,"No face detected.");
        FaceEnrollmentException failure=assertThrows(FaceEnrollmentException.class,
                ()->f.service.enroll(PASSWORD,true,()->false));
        assertTrue(failure.userMessage().contains("No face detected"));
        assertTrue(f.repository.records.isEmpty());
        assertEquals(0,f.camera.activeSessions);
    }

    @Test void multipleFaceEnrollmentFailureIsActionableAndSavesNoPartialRecord() throws Exception {
        Fixture f=new Fixture(Role.MANAGER); f.login();
        f.engine.failure=new FaceProcessingException(FaceProcessingException.Reason.MULTIPLE_FACES,"More than one face detected.");
        FaceEnrollmentException failure=assertThrows(FaceEnrollmentException.class,
                ()->f.service.enroll(PASSWORD,true,()->false));
        assertTrue(failure.userMessage().contains("More than one face detected"));
        assertTrue(f.repository.records.isEmpty());
        assertEquals(0,f.camera.activeSessions);
    }

    @Test void detectionExceptionIsDifferentFromCameraAccessFailure() {
        FaceEnrollmentException detection=FaceEnrollmentException.at(FaceEnrollmentException.Stage.CAPTURE_AND_FACE_PROCESSING,
                new FaceProcessingException(FaceProcessingException.Reason.DETECTION,"Face detection failed."));
        FaceEnrollmentException camera=FaceEnrollmentException.at(FaceEnrollmentException.Stage.CAPTURE_AND_FACE_PROCESSING,
                new CameraUnavailableException("Camera unavailable."));
        assertTrue(detection.userMessage().contains("Face detection failed"));
        assertTrue(camera.userMessage().contains("Camera access or frame capture failed"));
    }

    @Test void templateCipherRejectsTamperingAndAccountSwap() {
        Keys keys=new Keys(); AesGcmFaceTemplateCipher cipher=new AesGcmFaceTemplateCipher(keys);
        float[] raw=vector(0); FaceEmbedding vector=new FaceEmbedding(raw);
        EncryptedFaceTemplate encrypted=cipher.encrypt(9,vector);
        assertEquals(1,vector.similarity(cipher.decrypt(9,encrypted)) , 0.0001);
        assertThrows(IllegalStateException.class,()->cipher.decrypt(10,encrypted));
        byte[] ciphertext=encrypted.ciphertext(); ciphertext[0]^=1;
        var altered=new EncryptedFaceTemplate(encrypted.keyVersion(),encrypted.templateVersion(),encrypted.modelVersion(),encrypted.nonce(),ciphertext);
        assertThrows(IllegalStateException.class,()->cipher.decrypt(9,altered));
        assertFalse(Arrays.equals(vectorBytes(raw),encrypted.ciphertext()));
        vector.close(); Arrays.fill(raw,0f);
    }

    @Test void missingLocalKeyFailsWithoutPlaintextFallback() {
        Keys keys=new Keys(); AesGcmFaceTemplateCipher cipher=new AesGcmFaceTemplateCipher(keys);
        var template=cipher.encrypt(1,new FaceEmbedding(vector(0))); keys.present=false;
        assertThrows(IllegalStateException.class,()->cipher.decrypt(1,template));
    }

    private static byte[] vectorBytes(float[] values) {
        java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(values.length*4);
        for(float value:values)bytes.putFloat(value); Arrays.fill(values,0f); return bytes.array();
    }
    private static float[] vector(int active) { float[] values=new float[128]; values[active]=1; return values; }

    private static final class Fixture {
        final MutableClock clock=new MutableClock();
        final FakeUserDao users=new FakeUserDao();
        final Session session=new Session();
        final PasswordHasher hasher=new PasswordHasher(2_000);
        final AppUser user;
        final AuthService auth;
        final Repository repository=new Repository();
        final Keys keys=new Keys();
        final Camera camera=new Camera();
        final Engine engine=new Engine();
        final FacialVerificationService service;
        Fixture(Role role) {
            this(role,true);
        }
        Fixture(Role role,boolean loginEnabled) {
            user=users.insert("manager",hasher.hash(PASSWORD),role);
            auth=new AuthService(users,hasher,session,new PassThroughTransactionManager(),clock,AuditService.noop());
            Authorizer authorizer=new Authorizer(session);
            FaceModelFiles files=new FaceModelFiles(Path.of("unused")) { @Override public boolean available(){return true;} };
            service=new FacialVerificationService(camera,engine,files,repository,users,new AesGcmFaceTemplateCipher(keys),keys,
                    auth,authorizer,AuditService.noop(),clock,.363,Duration.ofSeconds(8),loginEnabled);
        }
        void login() throws Exception { auth.login("manager",PASSWORD); }
    }

    private static final class Keys implements TemplateKeyProvider {
        final byte[] key=new byte[32]; boolean present=true;
        String device="00000000-0000-4000-8000-000000000001";
        public String deviceId(){return device;}
        public byte[] createOrLoadKey(long userId){present=true;return key.clone();}
        public Optional<byte[]> loadExistingKey(long userId){return present?Optional.of(key.clone()):Optional.empty();}
        public void deleteKey(long userId){present=false;}
    }
    private static final class Repository implements BiometricDao {
        final Map<String,FaceEnrollmentRecord> records=new HashMap<>();
        private String key(long user,String device){return user+":"+device;}
        public Optional<FaceEnrollmentRecord> find(long id,String device){return Optional.ofNullable(records.get(key(id,device)));}
        public void save(FaceEnrollmentRecord r){records.put(key(r.userId(),r.deviceId()),r);}
        public int setEnabled(long id,String device,boolean enabled){var k=key(id,device);var r=records.get(k);if(r==null)return 0;records.put(k,new FaceEnrollmentRecord(r.userId(),r.deviceId(),r.ciphertext(),r.nonce(),r.keyVersion(),r.templateVersion(),r.modelVersion(),r.consentVersion(),r.consentedAt(),enabled));return 1;}
        public int deleteDevice(long id,String device){return records.remove(key(id,device))==null?0:1;}
        public int deleteAllForUser(long id){int before=records.size();records.keySet().removeIf(k->k.startsWith(id+":"));return before-records.size();}
    }
    private static final class Camera implements CameraProvider {
        int captures,activeSessions; Runnable afterCapture=()->{};
        public boolean available(){return true;}
        public CaptureSession open(Duration timeout,CancellationSignal cancellation) throws CameraUnavailableException {
            if(cancellation.cancelled())throw new CameraUnavailableException("cancelled"); activeSessions++;
            return new CaptureSession(){boolean closed;public CameraFrame capture(Duration t,CancellationSignal c)throws CameraUnavailableException{if(c.cancelled())throw new CameraUnavailableException("cancelled");captures++;afterCapture.run();return new CameraFrame(new Object(),()->{});}public void close(){if(!closed){closed=true;activeSessions--;}}};
        }
    }
    private static final class Engine implements FaceEngine {
        float[] vector=vector(0); FaceProcessingException failure; public boolean available(){return true;}
        public FaceEmbedding extract(CameraFrame frame)throws FaceProcessingException{if(failure!=null)throw failure;return new FaceEmbedding(vector);}
    }
    private static final class MutableClock extends Clock {
        private Instant now=Instant.parse("2026-10-26T00:00:00Z");
        void advanceSeconds(long seconds){now=now.plusSeconds(seconds);}
        @Override public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(java.time.ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
}
