# Optional facial verification — feasibility and current status

Facial verification is **not implemented as an authentication factor** in EstateFlow 1.2.0. The project has no camera capture provider, approved local face-matching model, tested liveness mechanism, consent/enrollment state store, protected template store, or Windows key-management integration. JavaFX controls alone do not provide reliable face authentication. Adding a camera preview or face detector would not make it safe to unlock an account.

The Settings page contains a disabled capability notice. `FacialVerificationService` reports unavailable/disabled, and no caller can use it to authenticate or bypass `AuthService`. Login remains username/password only; active-account checks, account status, and role authorization are unchanged. No image, embedding, biometric table, migration, network transfer, secret, or biometric backup is present. There is no enrollment, consent, revocation, deletion, or face-login button because no provider exists to safely complete those steps.

## Library and packaging assessment

OpenCV is a maintained cross-platform computer-vision library with Java bindings and Apache 2.0 licensing for current releases ([license](https://opencv.org/license/), [Java camera API](https://docs.opencv.org/4.4.0/javadoc/org/opencv/videoio/VideoCapture.html)). JavaCV packages native bindings and documents platform-specific Windows x64 artifacts; this adds native runtime, update, and packaging verification needs ([JavaCV project](https://github.com/bytedeco/javacv)). OpenCV/JavaCV can provide camera frames and vision primitives, but the reviewed official materials do not supply an end-to-end, tested biometric authentication and liveness design. Model weights have separate provenance and licensing from the runtime library.

ONNX Runtime publishes Java artifacts for CPU inference on Windows x64 and its project is MIT licensed ([Java API and supported targets](https://onnxruntime.ai/docs/get-started/with-java), [license](https://github.com/microsoft/onnxruntime)). It is an inference runtime, not a face matcher, anti-spoofing implementation, consent system, protected biometric store, or authentication policy. Adding it alone would not make verification safe.

No candidate model passed the required accuracy, false-accept/false-reject, demographic, spoof-resistance, support, and model-license review in this task. No biometric runtime, model, or native library was added to the Windows app image. The existing disabled service is the fail-closed integration boundary; password authentication remains the only login path.

## Work needed before enabling

1. Select a maintained local camera and matching stack with supported Java 25 / Windows x64 artifacts; review each native dependency, model provenance/license, update cadence, privacy behavior, and software supply chain.
2. Define measurable false-accept/false-reject targets and an independent liveness approach. Validate it against photos, screens, and replayed video. Face detection and embedding similarity alone are not liveness or production authentication.
3. Design an opt-in flow requiring a live authenticated session, password reauthentication, explicit purpose/retention consent, camera permission, account-active recheck, and immediate revocation. Keep password fallback available on every camera/provider failure.
4. If templates are retained, add a sequential manual migration only after design review. Store versioned encrypted templates, never raw frames by default. Keep encryption keys in Windows DPAPI/CNG or another OS-protected key store, never in source or the database beside ciphertext. Document machine migration, key rotation, recovery, deletion, and loss-of-key behavior. Encryption alone does not prevent matching attacks or guarantee a template can be rotated.
5. Add rate limits that affect only biometric attempts, generic failure responses, revoke/delete audit events without template material, and permission checks for enrollment management.
6. Add deterministic mocked-provider tests for consent, reauthentication, enrollment authorization, active/deactivated users, password fallback, revocation/deletion, rate limits, secret-safe logs, and provider failures. Then run physical-camera and presentation-attack tests on supported hardware before enabling the feature.

No physical-camera, liveness, matching-accuracy, or demographic performance test was performed because no provider or model is included. The current disabled state is the intended safe behavior, not a claim of completed face matching. No biometric test data was created.

## Current implementation update — EstateFlow 1.3.0

The earlier feasibility/status text above describes the prior disabled 1.2.0 snapshot. This section supersedes it for the current source tree: the OpenCV/JavaCPP face capture/matching, encrypted template lifecycle, DPAPI key protection, manual v1.8 migration, proof-based AuthService integration, login controls, and Settings enrollment/revoke UI are now implemented. The global gate remains off by default (`face.login.enabled=false`). DPAPI round-trip and mock-provider tests passed. Physical camera, model accuracy/spoof resistance, disposable MySQL migration, and final Windows app-image validation are not complete. There is no liveness detection. See `IMPLEMENTATION_GUIDE.md` and `docs/FACIAL_VERIFICATION_IMPLEMENTATION_STATUS.md` for the authoritative current details.
