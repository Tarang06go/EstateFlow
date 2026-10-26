# EstateFlow 1.2.0 — Windows x64

## Fixed

- Fixed Rent Collection Insights Refresh failing in late-payment behavior with MySQL Connector/J SQLState `S1009`. The JDBC DAO now binds only the placeholders present in the selected query.
- Database error dialogs now distinguish an operation failure from a MySQL service outage and display safe operation / SQLState / vendor-code details. Full causes remain in the per-user log.

## Security and compatibility

- Password login, disabled-account handling, and role authorization are unchanged.
- Optional facial verification remains unavailable and disabled. No camera, model, biometric template, biometric migration, or biometric data is included.
- No database migration is required for this fix.
- The Windows x64 app image bundles the Java runtime. MySQL 8.4 is installed and administered separately.

See `docs/DATABASE_FIX_RENT_COLLECTION_INSIGHTS.md` for the reproduced error and test limitation, `docs/PHASE_12_FACIAL_VERIFICATION.md` for the biometric feasibility review, and `docs/RELEASE_AND_BACKUP.md` for installation and recovery guidance.
