# HANDOVER

Newest entry on top. Read this first every session and update it last.

## Latest session: 2026-10-31 (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- Adopted the Defend What You Ship guide. Project is Lane B: real people's faces are involved.
- Read all app source: `MainActivity.kt`, `ui/CameraPreview.kt`, `ui/QualityState.kt`, manifest, backup rules, `app/build.gradle.kts`.
- Created this file and `CONSTRAINTS.md`.

In progress:
- Nothing. No app code was changed.

Current state of the app:
- Single screen: front camera preview with a warning banner from live quality checks (no face, multiple faces, too dark, too bright).
- It does not capture, save, or upload anything yet. Despite the name, no dataset is collected today.

Broken or risky:
- The project cannot build from this repo. `app/build.gradle.kts` uses `libs.*` and the root build needs a version catalog, but `gradle/libs.versions.toml` and `gradle/wrapper/` do not exist on disk. `/gradle/` is in `.gitignore` and was never in git history, so the original dependency versions are lost.
- `android:allowBackup="true"` with empty backup rules. Harmless now, but any face image saved to app storage later would be included in cloud backup and device transfer by default.
- Brightness is averaged over the whole frame, not the face, so a lit face on a dark background can read as too dark. Thresholds 100 and 220 are unverified.
- `QualityState.TooBlurry` exists but nothing sets it.
- The ML Kit analyzer runs on the main executor and allocates a full-frame `ByteArray` per frame. Possible UI jank, not measured.
- `FaceDetection.getClient` is called during composition without `remember` and is never closed. Likely recreated on every quality change. Not verified at runtime.
- Permission denial shows a static message with no retry or link to Settings.
- Owner confirmed no copy of `gradle/` exists, so it must be recreated. Not started: waiting for the owner to approve recreating it. Maven metadata `latest` values include prereleases (for example AGP `9.6.0-alpha02`), so pick stable versions and check that they support `minSdk = 21` and the existing `app/build.gradle.kts` API use.

- This machine has no JDK, Android SDK, `adb`, emulator, or `~/.gradle` (checked 2026-10-31), so nothing can be built or run here. No test device is connected either.
- Blocked request: the CCTV-Face-Recognition-Attendance-System session asked for a guided enrolment capture flow against a mock server. Nothing started. Waiting on the owner.

Next:
- Owner to review `CONSTRAINTS.md`.
- Restore the build: recreate `gradle/libs.versions.toml` and the wrapper, and remove `/gradle/` from `.gitignore`. This picks dependency versions, so log it in `DECISIONS.md`.
- Before any capture feature: write `THREAT.md` and `DATA.md`, covering consent, storage, retention, and deletion.

Do not touch:
- Nothing protected in code yet. See `CONSTRAINTS.md`.

Read first: `CONSTRAINTS.md`. `ARCHITECTURE.md` does not exist yet.
