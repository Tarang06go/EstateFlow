# EstateFlow v1.1 patch — how to apply

This patch contains **only changed and new files** (38 added, 48 modified, 0 deleted) plus three documentation files.
It is built against the project exactly as delivered in `EstateFlow_Updated.zip` (v1.0). `pom.xml` and `db/schema.sql` are **not** part of it.

## Before you start
1. Close the application and your IDE.
2. Make a backup copy of your EstateFlow folder (or commit to Git: `git add -A && git commit -m "before v1.1"`).
3. If you changed any v1.0 file yourself, compare it with the same-named file in this patch before overwriting. The modified files are
   listed in `CHANGELOG.md`; every file in the patch is a **complete** file, so a conflicting local edit must be merged by hand.

## Apply the code (no deletions or renames are needed)
Copy the **contents** of `EstateFlow_v1.1_Patch/` into your EstateFlow project root, merging folders and overwriting files when asked.
On Windows: select everything inside the patch folder, paste into the project folder, choose "Replace the files in the destination".
Command line (from the folder that contains both directories):

    cp -R EstateFlow_v1.1_Patch/. EstateFlow/        # macOS / Linux
    xcopy EstateFlow_v1.1_Patch EstateFlow /E /Y     # Windows

Then delete the old build output so nothing stale is reused: remove the `target/` folder (or run `mvn clean`).

## Optional database migration (enables Audit History)
The application runs without it: audit entries are simply not recorded and the Audit History screen says so.
The migration adds **one table, `audit_log`**; none of the seven v1.0 tables is touched. It is safe to run more than once.

1. Back up: `mysqldump -u <user> -p estateflow > estateflow_backup_before_v1.1.sql`
2. Apply:   `mysql -u <user> -p estateflow < db/migrations/v1.1_audit_log.sql`
3. Roll back if ever needed: `DROP TABLE audit_log;`

Never run migrations or tests against a database you cannot restore. For the integration tests, also apply the migration to
`estateflow_test` (`mysql -u <user> -p estateflow_test < db/migrations/v1.1_audit_log.sql`); without it the one audit test is skipped.

## Configuration
One new optional setting in `src/main/resources/config/application.properties` (already set in the patched file):

    rent.reminder.days=7      # days ahead listed on the Rent Reminders screen (0-90)

## Build and run
    mvn clean verify                 # unit tests (no database needed)
    mvn verify -Pintegration         # MySQL tests; needs db-test.properties and an estateflow_test schema
    mvn javafx:run                   # start the application from the project root (db.properties is read from there)

## What changed for users
Dashboard charts · property details with agreement/payment/maintenance history · global search box (sidebar), agreement filters and a
Payments screen with filters · PDF receipts (choose `.pdf` in the save dialog; `.txt` still works) and PDF/CSV reports ·
Rent Reminders screen · Maintenance analytics (button on the Maintenance screen) · Audit History screen.

## If something does not compile
The patch replaces service constructors (Owner, Tenant, Property, Agreement, Payment, Maintenance services now take audit/transaction
parameters) and the matching tests. If you have extra classes or tests of your own that construct those services, add the new
arguments shown in `AppContext.java`. Do not apply only part of the patch: apply all files together.

See `VERIFICATION_REPORT.md` for exactly what was tested and what was not.
