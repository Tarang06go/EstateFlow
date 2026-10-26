# EstateFlow facial verification 1.3.0 — file manifest

This is the allowlist for `EstateFlow_Facial_Recognition_Changes_1.3.0.zip`. All entries are project-relative. The ZIP contains no `.git`, `target`, model weights, configuration secrets, database dumps, user data, keys, or logs. `CHANGES.md`, `IMPLEMENTATION_GUIDE.md`, and `FILE_MANIFEST.md` are delivery documents at the ZIP root; the root `CHANGES.md` in the full project also preserves earlier EstateFlow change notes and has a facial-verification section appended.

## Delivery documents

- `CHANGES.md` — facial-verification 1.3.0 change summary.
- `IMPLEMENTATION_GUIDE.md` — safe application, migration, model, build/test, enrollment, revocation, and candidate instructions.
- `FILE_MANIFEST.md` — this allowlist.
- `docs/CHANGES.md` — source-tree copy of the facial-verification change summary.
- `docs/FACIAL_VERIFICATION_IMPLEMENTATION_STATUS.md` — current implementation and verification limits.
- `docs/PHASE_12_FACIAL_VERIFICATION.md` — existing phase note with a current-state update appended.

## Application and persistence

- `pom.xml`
- `db/migrations/v1.8_face_enrollment.sql`
- `scripts/download-face-models.ps1`
- `scripts/package-windows.ps1`
- `src/main/java/com/estateflow/EstateFlowApp.java`
- `src/main/java/com/estateflow/config/AppConfig.java`
- `src/main/java/com/estateflow/config/AppContext.java`
- `src/main/java/com/estateflow/controller/LoginController.java`
- `src/main/java/com/estateflow/controller/SettingsController.java`
- `src/main/java/com/estateflow/dao/BiometricDao.java`
- `src/main/java/com/estateflow/dao/UserDao.java`
- `src/main/java/com/estateflow/dao/jdbc/JdbcBiometricDao.java`
- `src/main/java/com/estateflow/dao/jdbc/JdbcUserDao.java`
- `src/main/java/com/estateflow/model/FaceEnrollmentRecord.java`
- `src/main/java/com/estateflow/model/enums/Role.java`
- `src/main/java/com/estateflow/security/Permission.java`
- `src/main/java/com/estateflow/service/AuthService.java`
- `src/main/java/com/estateflow/service/FaceEnrollmentException.java`
- `src/main/java/com/estateflow/service/FacialVerificationService.java`
- `src/main/java/com/estateflow/service/biometric/AesGcmFaceTemplateCipher.java`
- `src/main/java/com/estateflow/service/biometric/CameraFrame.java`
- `src/main/java/com/estateflow/service/biometric/CameraProvider.java`
- `src/main/java/com/estateflow/service/biometric/CameraUnavailableException.java`
- `src/main/java/com/estateflow/service/biometric/CancellationSignal.java`
- `src/main/java/com/estateflow/service/biometric/EncryptedFaceTemplate.java`
- `src/main/java/com/estateflow/service/biometric/FaceAttemptLimiter.java`
- `src/main/java/com/estateflow/service/biometric/FaceEmbedding.java`
- `src/main/java/com/estateflow/service/biometric/FaceEngine.java`
- `src/main/java/com/estateflow/service/biometric/FaceModelFiles.java`
- `src/main/java/com/estateflow/service/biometric/FaceProcessingException.java`
- `src/main/java/com/estateflow/service/biometric/KeyProtector.java`
- `src/main/java/com/estateflow/service/biometric/LocalDpapiTemplateKeyProvider.java`
- `src/main/java/com/estateflow/service/biometric/OpenCvCameraProvider.java`
- `src/main/java/com/estateflow/service/biometric/OpenCvFaceEngine.java`
- `src/main/java/com/estateflow/service/biometric/TemplateKeyProvider.java`
- `src/main/java/com/estateflow/service/biometric/WindowsDpapiKeyProtector.java`
- `src/main/java/com/estateflow/util/UiTask.java`
- `src/main/resources/config/application.properties`
- `src/main/resources/fxml/login.fxml`
- `src/main/resources/fxml/settings.fxml`

## Tests

- `src/test/java/com/estateflow/config/AppConfigTest.java`
- `src/test/java/com/estateflow/service/AuthServiceTest.java`
- `src/test/java/com/estateflow/service/FacialVerificationServiceTest.java`
- `src/test/java/com/estateflow/service/biometric/FaceAttemptLimiterTest.java`
- `src/test/java/com/estateflow/service/biometric/WindowsDpapiKeyProtectorTest.java`
- `src/test/java/com/estateflow/support/FakeUserDao.java`

Some integration files in this set are shared with earlier uncommitted EstateFlow work in the source tree. The ZIP includes only the specific facial feature integration paths listed here; it intentionally omits unrelated modified files and SQL backups.
