# Facial verification implementation status

## Implemented

EstateFlow 1.3.0 now contains an optional local face-verification flow built from OpenCV YuNet detection and SFace embeddings through JavaCPP/OpenCV bindings. The service captures five samples at enrollment and three at login, checks usability and sample consistency, encrypts normalized embeddings with AES-256-GCM, and stores only ciphertext, nonce, model/version metadata, consent metadata, enabled state, and device/user IDs. AES keys are wrapped with Windows DPAPI `CurrentUser` and stored under the current Windows user's Local AppData. Templates and keys are per user and Windows profile/device.

Enrollment requires an active non-admin user's own profile, explicit versioned consent, and password reauthentication before capture and before persistence. Existing enrollments can be disabled locally; revocation deletes all account enrollment rows and the current local wrapped key. Login produces a private, short-lived, one-use `FaceProof`; `AuthService` consumes it, reloads account state, excludes admins, updates last-login, and creates the normal session. Proof cancellation, expiry, shutdown, account deactivation and failed matching reject login. Password authentication is independent. Face attempts use a bounded, separate limiter.

The migration `db/migrations/v1.8_face_enrollment.sql` is additive and manual. No application startup code applies it. Models are downloaded by a separate PowerShell script and checked against pinned hashes; model binaries are not included in the project archive.

## Safety gate and verification limits

`face.login.enabled=false` is the default global setting. It must remain off until the Windows camera/native runtime, matching threshold, model behavior, false-accept/false-reject rates, and presentation attacks have been reviewed on supported hardware. No liveness detection or spoof resistance is implemented; photo/video replay may fool the matcher. The similarity reference in configuration has not been calibrated for EstateFlow users.

The Windows DPAPI protect/unprotect round trip was exercised on this host and passed. The latest focused facial/authentication/config run passed 32/32 tests. `mvn verify` compiled and ran 293 tests; 292 passed, with one unrelated `DocumentStorageTest` error because the sandbox denied a Windows temporary-file rename. A native smoke run loaded the local YuNet/SFace model files successfully. The user reports that their separate JShell check opened the webcam and captured frames; this agent did not capture a face or perform an enrollment. No enrollment-table metadata could be read from this sandbox because network socket access to MySQL was denied. No disposable-MySQL migration test or final Windows app-image validation was completed. `mvn clean verify` could not start its test phase because the running EstateFlow process held `target/classes/images/app-icon-16.png` open. The previously attempted jpackage step also failed with `Destination directory ... is not writable`; no app image or release archive was produced.

The old application log contains only the earlier camera-open failure (`No usable camera was found`); it does not contain the new post-capture exception reported by the user. The temporary `FACE DEBUG` and `printStackTrace` statements have been removed. Enrollment now reports a safe failure stage and diagnostic reference while preserving the full cause chain in the normal local log. Cancellation is checked after capture and before persistence, and the UI cancellation token is synchronized against the commit start. Therefore the precise post-capture failure in the user's separate running session is still unconfirmed; the next retry can identify its stage from the displayed reference and local log without logging biometric material.

## Deployment boundaries

The key is tied to its Windows user profile. A shared database does not make an enrollment portable between PCs; enroll separately on each authorized profile/device. Missing/corrupt local key material fails face authentication closed and retains password login. Encryption does not protect against malware running as the same Windows user.

No production release is approved by this status document. Build and test a separate 1.3.0 candidate, validate camera/model/migration behavior, and leave the global gate disabled until review is complete. Existing 1.2.0 release artifacts must remain untouched.
