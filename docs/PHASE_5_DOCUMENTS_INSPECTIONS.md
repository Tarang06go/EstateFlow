# Phase 5 — Documents and property inspections

## Documents

The Documents screen supports PDF, PNG and JPEG files up to 20 MiB. The application checks the extension and file signature, refuses symbolic-link inputs, and assigns an opaque UUID storage name. The original name is retained only as display metadata. Document bytes are stored in the current user's private folder at `%USERPROFILE%\.estateflow\documents` on Windows or `~/.estateflow/documents` on Linux/macOS. They are not stored in MySQL or the project directory. Only signed-in users can access documents; viewers can open files, while ADMIN and MANAGER can upload, replace and archive them. Replacement and archive retain the old metadata and stored bytes for history.

The root directory is created on first upload. Back up the complete `documents` folder together with the MySQL database. For recovery, restore the folder to the same location before opening document records. A metadata backup without the matching files cannot restore document contents. A storage-folder backup without the database metadata does not restore the EstateFlow listing.

The current upload form asks for the ID of the related property, lease agreement, tenant or maintenance request. The database enforces the selected relationship. Store only files appropriate for the account's authorized business use; document contents are not logged.

## Inspections

Inspections are associated with a property and may include checklist findings: area, condition (good/fair/poor/urgent), observation and a follow-up action. Drafts may be completed after adding at least one finding. Completed inspections remain in history and cannot be edited. An unlinked poor or urgent finding can create a normal EstateFlow maintenance request and records the resulting request link.

## Manual database migration

Back up first. Apply `db/migrations/v1.6_documents_inspections.sql` manually to the intended EstateFlow database after migrations v1.1–v1.5. Application startup never runs migrations. The script is additive and creates `property_document`, `inspection`, and `inspection_finding`; it leaves existing tables and records untouched. If the migration is missing, the new screens show no records and do not make schema changes.

Rollback requires exporting the three new tables before removing them. The migration header contains the SQL. Rollback permanently removes document metadata, inspection findings and inspection history. It does not remove document files, so restore the exported metadata and the matching storage backup before using those documents again.

## Verification status

`mvn test` was run on Java 25.0.4: 238 unit tests passed, with zero failures or skips. These tests include file signature, extension, size, symbolic-link, safe-name, inspection validation and authorization checks. MySQL integration and visual desktop checks have not been run for this phase. No database was changed.
