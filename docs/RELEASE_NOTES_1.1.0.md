# EstateFlow 1.1.0 release notes

## Added

- Advanced rent collection insights are available in the existing Advanced Analytics screen: period-due charges, collection allocated to those charges through period end, valid cash receipts by payment date, and receipts applied to earlier due periods.
- Collection trends, property and permission-gated tenant breakdowns, current arrears aging, overdue charge follow-up, late-settlement history, and CSV/PDF export use existing rent charges and payment records. No schema migration is required.
- Settings explains that optional facial verification is unavailable and disabled. Password sign-in is unchanged; this build has no camera provider, face matcher, liveness detection, enrollment flow, or biometric storage.
- CSV export neutralizes formula-leading text to reduce spreadsheet formula injection risk while keeping negative numeric amounts numeric.
- Backup/recovery guidance now covers dump validation, explicit disposable restore targets, application write coordination, privileges, uploaded documents, and biometric-data status.

## Financial definitions

- Charges are live rent charges whose **due date** falls inside the inclusive selected period. The schema has no rent-charge creation timestamp, so the report does not label them as charges created during that period.
- Collection percentage is valid payments applied to those selected-period charges through the selected end date divided by those charges' billed amount. Payment dates before the selected start still count if they reduced one of those charges. The rate is zero when billed amount is zero.
- Cash receipts are valid payments dated inside the selected period on non-cancelled charges. A separate subtotal identifies those receipts applied to charges due before the period. Cancelled charges and voided payments are excluded.
- Current arrears and follow-up are calculated as of the report date. The four aging buckets use 1–30, 31–60, 61–90, and over 90 calendar days after due date. Partial payments reduce each charge balance; one charge is counted once.
- Late-settlement history includes fully settled charges due in the selected period, capped to the latest 12 months. The displayed “repeated late” indicator requires at least three fully settled charges and at least three settled after their due dates. It is descriptive and does not predict tenant behavior. Fewer than three settled charges is labeled insufficient history.

## Database and installation

There are no new database migrations. Keep the database configuration in `%USERPROFILE%\.estateflow\db.properties`. Windows x64 prerequisites are a supported Windows x64 version and access to MySQL 8.4 with the EstateFlow schema and prior manual migrations. Java is bundled in the app image; MySQL is not. Facial verification is disabled and stores no biometric data.

Before use, create or select the intended schema, apply only the migrations needed by the existing installation, set up the first administrator, and confirm the app points to the intended database. Never restore a backup to the live schema as a test. See `RELEASE_AND_BACKUP.md` for full installation and recovery procedures.

## Verification status

`mvn verify` passed 274 unit tests with 0 failures and 0 skipped and produced the 1.1.0 JAR/JaCoCo report. `mvn clean verify` was attempted but Windows could not delete `target/classes/images/app-icon-16.png` because another process held it open; the holder was not identified. MySQL integration did not run because no `db-test.properties` was present. FXML XML parsing passed for all 51 files; a real-display UI, release launch against a disposable schema, operational restore drill, and physical-camera test were not run. The release archive was read in full for ZIP integrity, checked for excluded local data, and SHA-256 hashed.

This is a packaged release candidate, not a production-verified deployment. Complete isolated MySQL, real-display Windows, and operational restore checks in the target environment before production use.
