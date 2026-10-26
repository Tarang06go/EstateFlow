# EstateFlow 1.3.0 — Windows setup, deployment, and recovery

## Supported deployment

EstateFlow is a Windows x64 desktop client. It connects directly to MySQL 8.4 over JDBC; MySQL is separate and may run on the same computer or on a private, access-controlled network. The packaged app image bundles a Java runtime and the Windows x64 application dependencies. It does not bundle MySQL, database credentials, or face-recognition model files. Do not expose MySQL directly to the public internet. For several remote users or internet access, put a separately designed authenticated backend API between clients and the database; this release does not provide one.

Build and verify from a Windows x64 Java 25 JDK with Maven:

```powershell
mvn verify
$release = "$HOME\Downloads\EstateFlow-1.3.0"
./scripts/package-windows.ps1 -OutputRoot $release -AppVersion 1.3.0
./scripts/create-installer.ps1 -AppImagePath "$release\EstateFlow-1.3.0-win-x64" -OutputDirectory $release -AppVersion 1.3.0
./scripts/create-release.ps1 -AppImagePath "$release\EstateFlow-1.3.0-win-x64" -InstallerPath "$release\EstateFlow-1.3.0-Windows-x64-Setup.exe" -OutputDirectory $release -AppVersion 1.3.0
```

The packaging commands produce a portable app image, a per-user Windows `.exe` installer, a portable ZIP, release notes, and SHA-256 manifests. Both the installer and app image bundle Java; MySQL remains separate. `jpackage` requires WiX Toolset v3.0 or later on the Windows build machine. Supply its `bin` directory with `-WixBinPath` when WiX is not installed in its standard location. Keep generated packages outside the source tree and never include local config or database backups.

## Fresh database installation

Use MySQL 8.4 and a new, empty schema. These commands create schema objects only; demo data is separate and optional. Back up any existing schema before applying SQL. Do not run the initialization sequence against a database containing records. From Command Prompt (`cmd.exe`) opened at the project/release folder, apply:

```cmd
mysql -u <setup-admin> -p < db/schema.sql
mysql -u <setup-admin> -p estateflow < db/migrations/v1.1_audit_log.sql
mysql -u <setup-admin> -p estateflow < db/migrations/v1.2_app_user.sql
mysql -u <setup-admin> -p estateflow < db/migrations/v1.3_audit_actor.sql
mysql -u <setup-admin> -p estateflow < db/migrations/v1.4_expense.sql
mysql -u <setup-admin> -p estateflow < db/migrations/v1.5_deposits_leases.sql
mysql -u <setup-admin> -p estateflow < db/migrations/v1.6_documents_inspections.sql
mysql -u <setup-admin> -p estateflow < db/migrations/v1.7_vendors_work_orders.sql
mysql -u <setup-admin> -p estateflow < db/migrations/v1.8_face_enrollment.sql
```

The v1.1 through v1.8 migrations are additive and must be applied in order for all 1.3.0 modules. In particular v1.3 depends on the audit tables from v1.1 and v1.2. EstateFlow never applies migrations automatically. Demo data is optional and only appropriate in a disposable database. The `db/seed.sql` file is available in the source project for that purpose and is intentionally omitted from the release archive. Never seed an existing or production database.

Create a dedicated MySQL application account restricted to the EstateFlow schema. For ordinary use, grant only `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on `estateflow.*`; grant DDL rights only to the operator applying reviewed migrations, then remove them if practical. The first-administrator screen creates the initial EstateFlow account after migration; there is no preset application administrator. Protect MySQL with a firewall, TLS for private-network connections, strong unique passwords, and backups. Avoid using the MySQL root account in the application.

## Install and first launch

1. Run `EstateFlow-1.3.0-Windows-x64-Setup.exe` and follow the per-user setup prompts. The installer creates a Start Menu shortcut and bundles Java; Java and Maven are not required on the target computer. Alternatively, extract the portable ZIP and launch `EstateFlow-1.3.0-win-x64.exe` from its app-image folder. MySQL is installed/administered separately.
2. Copy `db/db.properties.example` to `%USERPROFILE%\.estateflow\db.properties`. Create the `.estateflow` folder if needed, replace the example username/password, and set the JDBC URL to the chosen local or private-network MySQL server. Keep this file private and outside the installation folder.
3. Launch the app. Complete first-administrator setup, then sign in. EstateFlow does not restore backups, seed data, or migrate the database at startup.

Configuration lookup is: `-Destateflow.db.config=<path>`, `ESTATEFLOW_DB_CONFIG`, `%USERPROFILE%\.estateflow\db.properties`, then legacy working-directory files. `db/db.properties.example` is a sanitized template only. The normal face-login default in `src/main/resources/config/application.properties` is false. The local system property `-Destateflow.face.login.enabled=true` overrides that default explicitly; do not put this override into a normal release shortcut.

Writable preferences, logs, documents, and DPAPI-protected face keys are kept under the current user's profile (`%USERPROFILE%\.estateflow` and `%LOCALAPPDATA%\EstateFlow`). The optional face models are not distributed in the app image. If face login is being evaluated on a Windows machine, first provision the pinned models with `scripts/download-face-models.ps1`, apply v1.8, enroll with consent, and test login on that same Windows user/device. Face templates are encrypted and device/user-bound. Do not copy face keys or templates between accounts or machines.

## Updating

Close EstateFlow, take and verify a database and document-store backup, install the new app image alongside or over the old application folder, then manually apply only the new release migrations in their documented order. Keep `%USERPROFILE%\.estateflow` and the existing database. Reconfigure nothing if the user config path is unchanged. Verify sign-in and representative screens before removing the old app image. Never run schema creation or demo seeding over an existing database.

## Backup and restore

Back up both the MySQL database and `%USERPROFILE%\.estateflow\documents`; database rows can refer to stored files. Close EstateFlow first for a coordinated point-in-time copy. Keep credentials in a protected MySQL option file outside the project and command history. For example:

```powershell
$backup = Join-Path $HOME ("Documents\EstateFlowBackups\" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup | Out-Null
$dumpPath = Join-Path $backup 'estateflow.sql'
if (Test-Path -LiteralPath $dumpPath) { throw 'Backup target already exists.' }
mysqldump --defaults-extra-file="$HOME\.my.cnf" --single-transaction --routines --events --triggers --hex-blob "--result-file=$dumpPath" estateflow
if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed; do not treat this backup as complete.' }
Copy-Item -LiteralPath (Join-Path $HOME '.estateflow\documents') -Destination (Join-Path $backup 'documents') -Recurse
Get-FileHash $dumpPath -Algorithm SHA256
```

Verify that the dump is non-empty and restore it into a newly created disposable schema before relying on it. A checksum detects changes but does not prove restoratability. Protect backups as sensitive property/tenant data. To restore, stop the app and use an empty, explicitly verified schema and a separate document directory first; configure a separate `db.properties` and `ESTATEFLOW_DOCUMENTS_ROOT` for the drill. Never test restore against the live schema. For production recovery, use the organization's reviewed change process and restore the matching document backup as well.

## Security and known limitations

- Database passwords and user data must stay outside the app image and source control. Release archives must contain no `db.properties`, test config, SQL backups, `.estateflow` data, logs, or documents.
- Face login remains disabled by default. No validated liveness or presentation-attack detection is implemented, and the similarity threshold has not undergone independent false-accept/false-reject calibration. Facial login is not approved as a high-assurance or production authenticator; password sign-in remains available.
- Face models are downloaded separately by a script that verifies pinned SHA-256 values. Review model provenance/licenses and keep model files in the per-user local data folder.
- Direct JDBC connectivity is intended for a trusted local/private network. Internet-facing multi-user deployment requires a backend API, authorization, TLS, and operational hardening that are outside this release.

## Release checks

Run `mvn verify`, package on Windows x64, inspect the app image's `app` and `runtime` directories, then smoke-launch on a clean Windows account with a disposable MySQL schema. Test first-administrator setup, password sign-in, a read-only report, document storage/open, and update without changing the database. Test facial enrollment/sign-in only as a separate optional device-specific check; automated tests are not biometric verification. Check the release archive for private files before sharing it. No database or document store is modified by the packaging scripts.
