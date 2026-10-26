# EstateFlow 1.3.0 — Windows x64

## Included

- Property, rental, payment, maintenance, expense, deposit, document, inspection, vendor/work-order, analytics, notification, audit, and local property-assistant features.
- Optional local face enrollment/sign-in, disabled by default. The global release default remains `face.login.enabled=false`.
- Windows x64 app image with a bundled Java runtime and Windows native dependencies.
- Per-user Windows setup executable with Start Menu and desktop shortcuts, plus a portable ZIP and SHA-256 manifests.

## Setup

Follow `RELEASE_AND_BACKUP.md`. Prepare a fresh MySQL 8.4 schema using `db/schema.sql` and migrations v1.1 through v1.8 in order, then configure `%USERPROFILE%\.estateflow\db.properties`. MySQL is not bundled. No database migration, restore, or demo seed runs at application startup.

## Security limitations

Face recognition models are provisioned separately using the hash-verifying `scripts/download-face-models.ps1`; they are not inside the app image. Facial login stays off by default and does not include validated liveness or presentation-attack protection. Treat it as experimental; password login remains available. Do not expose MySQL publicly.

## Verification status

This document describes the release configuration and setup contract. Refer to the task's build report for the actual Maven test result, package artifact, and whether an installed-machine smoke test was performed.
