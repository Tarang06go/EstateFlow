# EstateFlow 1.3.0 facial verification implementation guide

## Current release posture

This change set adds optional local face enrollment and matching. Password login remains available and unchanged as the fallback. The code stores encrypted face embeddings only: camera frames and plaintext embeddings are not persisted. Administrator accounts cannot enroll or use face login. There is no liveness or presentation-attack detection; a photo or video replay may fool the matcher.

**Face login is globally disabled by default** with `face.login.enabled=false` in `src/main/resources/config/application.properties`. Enrollment and tests can be reviewed while this gate is off. Do not change it to `true` for production until a separate release review has validated the model threshold, camera behavior, false-accept and false-reject rates, and presentation attacks on supported hardware. The checked-in threshold is an experimental reference, not a validated production threshold.

YuNet's detector confidence cutoff is separately configured as `face.detection.score.threshold` (default `0.70`, allowed `0.50`–`0.90`). It was lowered from the previous hard-coded `0.90` to improve recall on ordinary webcam captures while preserving the exact-one-face requirement and face-size, lighting, five-sample consistency, and SFace checks. This detector setting has not been calibrated against a real user camera; login stays disabled. Privacy-safe diagnostics record per-frame detection counts and image dimensions, without logging image pixels or features.

The migration is manual and is never applied by application startup. The migration, native camera path, model downloads, and full release have not been validated against a disposable MySQL database, physical camera, or final Windows release package in this environment. The changes ZIP is not a signed or production release.

## Apply the source changes safely

1. Stop EstateFlow and make a copy of the existing project folder. Extract the changes archive into that copy, preserving the archive's project-relative paths. Review the diff before replacing files in a working project; some shared integration files may already contain unrelated EstateFlow work.
2. Confirm `pom.xml` reports version `1.3.0`. Keep the existing 1.2.0 app image and release archive unchanged.
3. Keep `face.login.enabled=false`. Configure a private per-user database properties file outside the project, as described in the project README. Do not add credentials to the source tree or archive.

## Back up and review the database migration

Run PowerShell from the project root. First confirm that the selected MySQL client points to the intended database. Use a protected MySQL option file (not a password on the command line):

```powershell
$backup = Join-Path $PWD ("estateflow_backup_before_v1.8_{0}.sql" -f (Get-Date -Format 'yyyyMMdd_HHmmss'))
mysqldump --defaults-extra-file="$env:USERPROFILE\.estateflow\mysql-backup.cnf" --result-file="$backup" --single-transaction --routines --triggers estateflow
if ($LASTEXITCODE -ne 0) { throw 'Database backup failed; do not apply the migration.' }
Get-FileHash -Algorithm SHA256 $backup
```

Review `db/migrations/v1.8_face_enrollment.sql` before applying it. It creates one additive InnoDB table, depends on the existing `app_user` table from migration v1.2, and has no data backfill. It does not change existing accounts. The rollback comment drops the enrollment table and permanently deletes all face-enrollment records. Do not apply it to the live database until the backup is verified and the migration has been rehearsed against a disposable database copy.

To rehearse or apply after that review, use a private MySQL option file and the exact intended schema:

```powershell
Get-Content -Raw .\db\migrations\v1.8_face_enrollment.sql | mysql --defaults-extra-file="$env:USERPROFILE\.estateflow\mysql-backup.cnf" estateflow
if ($LASTEXITCODE -ne 0) { throw 'Migration failed; inspect the database before retrying.' }
mysql --defaults-extra-file="$env:USERPROFILE\.estateflow\mysql-backup.cnf" estateflow -e "SHOW CREATE TABLE user_face_enrollment\G"
```

The application does not execute this step automatically. If rollback is required, first stop the application and take another database backup, then have the operator review and run `DROP TABLE user_face_enrollment;`. That removes all face enrollments and consent records.

## Download and verify the local face models

The models are not embedded in this archive or in the application JAR. Inspect the upstream model licenses and provenance first. Run the checked-in downloader in PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\download-face-models.ps1
```

It saves YuNet and SFace under `%LOCALAPPDATA%\EstateFlow\models`, checks the hard-coded SHA-256 hashes, preserves mismatched files, and does not add the weights to Git or the ZIP. The Windows runtime uses local OpenCV/JavaCPP artifacts and Windows x64 native dependencies; it does not download models at application startup.

## Build and test

Use a Java 25 JDK and Maven. From the project root:

```powershell
mvn test
mvn verify
```

The focused facial and authentication regression tests can be run with:

```powershell
mvn '-Dtest=FacialVerificationServiceTest,WindowsDpapiKeyProtectorTest,FaceAttemptLimiterTest,AuthServiceTest,AppConfigTest' test
```

Check the final Maven exit code and Surefire reports. A passing mocked-provider suite does not validate the physical camera, model accuracy, database migration, liveness, or release package.

Current troubleshooting verification: the 32 focused facial/authentication/config tests pass. The latest full `mvn verify` run reports 293 tests, 292 passed, and one unrelated `DocumentStorageTest` temporary-file rename error denied by this sandbox. `mvn clean verify` could not clean `target` while the running EstateFlow app held an icon resource open. A local native smoke test initialized YuNet/SFace successfully. The latest post-capture failure from the separate user-run session is not available in this workspace log; enrollment now emits a safe stage and diagnostic reference and preserves the cause chain in the local log.

## Enrollment and fallback workflow

After the v1.8 migration is reviewed and applied to the intended database, launch EstateFlow and sign in with a non-admin account. Open Settings, re-enter the account password, read and accept the explicit face-enrollment consent, then capture the five enrollment samples. The local AES key is wrapped with Windows DPAPI for the current Windows user; each user profile/device has its own enrollment. Keep the feature gate off during initial validation.

For a password fallback check, leave `face.login.enabled=false`, restart the app, and verify password sign-in still works. Camera denial, missing models, damaged templates, a missing local key, and verification failure must also leave password sign-in usable. Face attempts are throttled independently of password attempts.

Once global enablement has passed its separate security review, an enrolled non-admin user may enable or disable face sign-in for this Windows profile in Settings. Enablement still requires the current user's password. The login screen's face control depends only on the global gate and local provider readiness; it does not expose whether a particular username has an enrollment. The actual attempt checks account status, role, consent, enrollment and local key, then returns a generic failure if any check fails.

To disable only this profile, use Settings and reauthenticate. To revoke consent across devices, use **Revoke and delete all enrollments**; this deletes the account's database enrollment rows and this profile's wrapped key. If that key is missing or corrupt, face sign-in fails closed and the account must use the password and enroll again. Password sign-in is never removed.

## Build a separate Windows app-image candidate

The packager refuses to overwrite an existing output folder. Use a new destination and the isolated Maven repository if available:

```powershell
$repo = Join-Path $PWD 'target\maven-repo'
$candidateRoot = Join-Path $PWD 'release-candidates'
.\scripts\package-windows.ps1 -OutputRoot $candidateRoot -AppVersion '1.3.0' -MavenArguments @('-o', "-Dmaven.repo.local=$repo")
```

The output is a Windows x64 app image with the native-access launcher option. This is a packaging candidate, not a signed or approved release. Confirm the app starts, password login works, native dependencies resolve, local models load, camera open/close works, and database migration/revocation behave correctly before preparing a separately reviewed release archive. Keep any existing 1.2.0 distribution untouched. Do not enable face login until hardware and spoof/accuracy review is complete.

In the current restricted environment, Maven successfully produced `target\estateflow-1.3.0.jar`, but the jpackage step failed with `Destination directory ... is not writable` in each approved writable root tested. No app-image candidate or release archive was produced. Retry the command in a normal Windows development environment with a writable output folder before treating packaging as complete.

## Security boundaries and known limits

- DPAPI `CurrentUser` protects the AES key for the Windows profile where it was created. Copying the database to another PC does not copy a usable key; enroll separately on every profile/device.
- The matcher uses YuNet detection plus SFace embeddings. Similarity is not proof of a live person, and the checked-in threshold is not production-calibrated.
- No physical camera, demographic/accuracy, false-accept/false-reject, presentation-attack, or disposable-MySQL migration test was completed for this change.
- The face-login global setting remains off until those validations have been performed and reviewed.
