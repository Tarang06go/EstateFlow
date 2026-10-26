# Changes — facial verification 1.3.0

- Added local YuNet/SFace camera capture and face embedding pipeline using JavaCPP/OpenCV Windows x64 dependencies.
- Added AES-256-GCM encrypted templates, per-Windows-profile DPAPI-protected keys, explicit consent, password reauthentication, local disable, global revoke/delete, separate face-attempt throttling, and single-use expiring/cancellable login proofs.
- Integrated the feature with `AuthService`, permissions, login UI, Settings, app lifecycle, and an additive manual v1.8 migration.
- Added integrity-pinned model-download and Windows app-image candidate packaging scripts.
- The global face-login gate defaults off. Password login remains available. There is no liveness detection or spoof resistance.
- Follow-up hardening: removed temporary console dumps, added stage-coded enrollment diagnostics with local cause-chain logging, fixed cancellation after the final frame so it cannot proceed into persistence, synchronized UI cancellation against commit start, and clear the extracted feature array after embedding construction.
- Validation: 32 focused facial/auth/config tests passed. Windows DPAPI round-trip and local native YuNet/SFace initialization passed. Full verification ran 293 tests: 292 passed, 1 existing `DocumentStorageTest` error from sandbox-denied temporary-file rename. `mvn clean verify` stopped because the running app held a target image open. The jpackage destination was not writable. No agent-run physical-camera enrollment, disposable-MySQL migration test, or final app-image validation was completed. The current post-capture runtime exception has not been captured from the user's latest session; the new diagnostics preserve it safely for the next retry.
