# HANDOVER

Newest entry on top. Read this first every session and update it last.

## Latest session: 2026-11-01, review findings (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- 21 Codex PR bot comments were found unread after merging. Codex, a separate review, and the bot found real defects, fixed in one PR with four commits.
- Stale work: running calls stop when an enrolment or session ends (D-017). A late photo can no longer reach the next person. Decline and server change wait for a running call. The background timeout now fires while away. Back waits for checks to finish.
- Trust boundaries: port range, `notice_url` must stay on the same server, `client_name` cleaned and capped, full address shown, client checked before each notice.
- Upload retry sends identical bytes, so a manual retry recovers the job. Capture rejects a rocking head and a face past the frame edge.
- Tools: image guard handles non-ASCII names, mock checks are atomic, old job polls no longer change status, mock logs hide record IDs.
- CI runs `gitleaks` on new commits.

In progress:
- Nothing.

Broken or risky:
- Read Codex's PR comments before every merge. Green CI is not a review.
- Secrets: `gitleaks` 8.30.1 scanned all history once on 2026-11-01, with the owner's permission: 41 commits, 0 leaks. CI scans new commits only.
- Codex hit its usage limit until 2026-11-04, so it never answered the `client_name`, `notice_url`, `selectPerson`, and `QrAnalyzer` findings.
- `CameraLifecycleTest` has not run since `CaptureCamera` changed. Run it first in the real-face test.
- Consent can be withdrawn in the app only before upload (D-018). On a re-enrolment the old enrolment stays in service, and the app says so. Whether it should also end is a legal question for the owner.
- Pre-ship gate: not ready to ship. Setup codes are not signed (D-014). ML Kit metrics reach Google (D-013).

Next:
- Owner's real-face test against the mock, signed off for the owner only (D-015), then threshold tuning. Tell the owner before anything opens the camera.
- Staff sign-off: legal basis, final notice, withdrawal contact, retention.
- Release build: signing, plus private CA if the server sits on a LAN.

Do not touch:
- `QualityPolicy.kt` values, until the real-face test gives measurements.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`.

## Earlier session: 2026-11-01, real-face test preparation (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- The capture screen shows live numbers against the mock, and logs only prompt counts, timings, and sizes (D-016). The test script is in `TESTING.md`.
- `ReEnrolTapPathTest` taps the real screens from connect to the notice, with no camera.
- Release build checked: not debuggable, no cleartext, backup off, expected permissions only. Unsigned, so never run.
- Lane B docs added: `ALTERNATIVES.md`, `FAILURE.md`, `ROLLBACK.md`, `defense/DEFENSE-enrolment-app.md`, `defense/PRE-SHIP-GATE.md`.
- Owner signed off the owner's own real-face test only (D-015).

In progress:
- Nothing.

Broken or risky:
- Pre-ship gate: not ready to ship, as expected. 5 pass, 2 partly, 4 fail, 3 owner only. See `defense/PRE-SHIP-GATE.md`.
- `serverFailure_keepsPhotosAndRetrySucceeds` failed once, cause not proven. See `FAILURE.md`.
- `CameraLifecycleTest` has not run since `CaptureCamera` changed. Run it first in the real-face test.
- The readout and re-enrol tap test have not run on the tablet, which needs an install tap.
- Setup codes are not signed (D-014). ML Kit metrics reach Google (D-013). Nothing compares a re-enrolled face with the old one.

Next:
- Owner's real-face test against the mock, signed off for the owner only (D-015), then threshold tuning. Tell the owner before anything opens the camera.
- Staff sign-off: legal basis, final notice, withdrawal contact, retention.
- Release build: signing, plus private CA if the server sits on a LAN.

Do not touch:
- `QualityPolicy.kt` values, until the real-face test gives measurements.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`.

## Earlier session: 2026-11-01, light theme and slow server (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- Light theme checked by screenshot on review, upload, accepted, and rejected: phone, and tablet in both orientations. The wide roster rows now match in height.
- Dialogs were Material's default lavender in the light theme. Fixed once in the theme, through `surfaceContainerHigh`.
- Mock `slow` scenario tested, and a new `timeout` scenario: the job commits and the answer comes after the app gives up. The retry reuses the key and finds the one job.
- `EnrolmentFlowTest`: 2 of 2 runs `OK (12 tests)` on each device, 0 crash lines.

In progress:
- Nothing.

Broken or risky:
- The app's upload timeouts are still first guesses, and the mock runs on loopback. Real networks are not tested.
- The cancel dialogs on capture and review take the same theme fix, but were not seen, because they need the camera flow.
- CCTV confirmed `enrolment_open` and `reason_required` for the provisional 8c contract. The server is not built.
- A new consent per re-enrolment is approved for the owner's test only (D-015). Staff use is not signed off, and needs legal basis, final notice, withdrawal contact, and confirmed retention.
- Nothing compares a re-enrolled face with the old one. See `THREAT.md`.
- Setup codes are not signed. They must be before the first outside client (D-014). Until then the owner hands out codes.
- ML Kit telemetry stays, by owner decision (D-013). It reaches Google with device details and a per-install ID.

Next:
- Owner's real-face test against the mock, signed off for the owner only (D-015), then threshold tuning. Tell the owner before anything opens the camera. Also tap through re-enrol by hand, and scan a QR code on the tablet.
- Release build: signing, plus private CA if the server sits on a LAN.

Do not touch:
- `QualityPolicy.kt` values, until the real-face test gives measurements.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`.

## Earlier session: 2026-11-01, re-enrol (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- Re-enrol with a reason (D-012): a button on enrolled rows, a confirm dialog, then the normal notice, new consent, capture, review, and upload with `reason`. A rejected re-enrolment keeps the old enrolment.
- Mock: reasons, one open job per person, `previous` status on rejection, `POST /mock/reset`.
- `EnrolmentFlowTest` keeps an empty activity open, because OnePlus froze the process at about 27 s and the run hung with no failure. After the fix: 3 of 3 runs `OK (10 tests)` on each device, 0 crash lines.

In progress:
- Nothing.

Broken or risky:
- CCTV confirmed `enrolment_open` and `reason_required` for the provisional 8c contract. The server is not built.
- A new consent per re-enrolment is the cautious default, agreed with CCTV. The owner has not ruled on it yet.
- Nothing compares the new face with the old one, so staff could replace someone's enrolment. See `THREAT.md`.
- The equal row height on the wide roster was built after the last tablet install and has not been seen on the tablet. The test for it would need a tap on the tablet's install prompt.
- Setup codes are not signed. They must be before the first outside client (D-014). Until then the owner hands out codes.
- ML Kit telemetry stays, by owner decision (D-013). It reaches Google with device details and a per-install ID.

Next:
- Light theme and slow-server checks, plus the wide roster row height on the tablet.
- Owner sign-off: notice text, including Google's ML Kit metrics (D-013), legal basis, named test person, new consent per re-enrolment.
- Owner's real-face test, then threshold tuning. Tell the owner before anything opens the camera.

Do not touch:
- `QualityPolicy.kt` values, until the real-face test gives measurements.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`.

## Earlier session: 2026-11-01, QR setup (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- QR setup with ZXing core 3.5.4 (D-011). Typed address stays as the fallback. A scanned code must name the client that answers, or nothing is stored.
- The camera now unbinds when its screen leaves, for capture too. It used to stay open behind later screens.
- Real scan on CPH2381 against the mock: matching code reached sign-in, wrong-client code was refused, no active camera client afterwards. `CameraLifecycleTest` and `EnrolmentFlowTest` pass on both devices.

In progress:
- Nothing.

Broken or risky:
- Setup codes are not signed, so a forged code can point a device at another HTTPS server. See `THREAT.md`.
- Real scan not run on OPD2203, or in poor light, or at an angle. `ImageAnalysis` uses its default resolution.

Next:
- Re-enrol action with a reason (contract in `features/FEATURE-001-enrolment.md`).
- Audit ML Kit telemetry, and disable it if possible.
- Light theme and slow-server checks.
- Owner's real-face test, then threshold tuning. Tell the owner before anything opens the camera.

Do not touch:
- `QualityPolicy.kt` values, until the real-face test gives measurements.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`.

## Earlier session: 2026-11-01 (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- PRs #1 to #15 merged into `main` with merge commits, 0 conflicts, after a local rehearsal. `main` matches #15 exactly. Tag `pre-merge-2026-11-01` marks the old `main`. Plan and evidence: `Face-Dataset-Collector-merge-plan/MERGE-PLAN.md`, beside the repo.
- CI ran for the first time and passed on #15: 5 min 59 s with an empty cache.
- Owner accepted `CONSTRAINTS.md` and kept the JetBrains template in `.gitignore`.
- The app runs end to end against the mock: connect, sign in, roster, consent, guided capture, review, upload, result. Phone and tablet layouts, dark and light themes.
- `EnrolmentFlowTest` passes on both devices with synthetic images and no camera.

In progress:
- Nothing. The 15 merged remote branches still exist.

Broken or risky:
- All capture thresholds in `QualityPolicy.kt` are first guesses, including `YAW_SIGN` for left and right. Not calibrated on a real face.
- ML Kit telemetry (`transport-backend-cct`) is unaudited. See `THREAT.md`.
- On 2026-10-31 a real face was in view during a device check and was likely auto-captured. It stayed in memory and was cleared. No image was found on either device.
- The real CCTV server is not an approved destination yet. D-004 covers the mock only.
- Not tested: light theme on the review, upload, and rejected screens; the mock `slow` scenario; real networks.

Next:
- QR setup.
- Re-enrol action with a reason (contract in `features/FEATURE-001-enrolment.md`).
- Audit ML Kit telemetry, and disable it if possible.
- Light theme and slow-server checks.
- Owner's real-face test, then threshold tuning. Tell the owner before anything opens the camera.

Do not touch:
- `QualityPolicy.kt` values, until the real-face test gives measurements.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`.

## Earlier session: 2026-10-31, afternoon (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- Owner approved the enrolment work, recreating `gradle/`, opening PRs, and CI/CD. This entry replaces the blockers listed in the earlier entry below.
- Build restored on latest stable. `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass in 2m 27s, with 9 lint warnings. See D-001.
- Smoke test on both devices, installed and launched, 0 crashes. Start time: CPH2381 API 34 2213 ms, OPD2203 API 36 1216 ms. Install over wireless adb: 3.9 s and 170 s.
- Plan agreed with Codex and CCTV contract received: `features/FEATURE-001-enrolment.md`.
- Drafted `THREAT.md` and `DATA.md`.

- ML Kit analysis moved to a background thread (D-007). Measured per frame: CPH2381 about 56 to 64 ms, OPD2203 about 61 to 71 ms, scene unknown. Install was 3.8 s and 22 s this time, so the earlier 170 s was not typical.

In progress:
- Stacked PRs from the plan. CI has never run: GitHub refused the job over account billing, so the workflow is unproven.

Broken or risky:
- `CONSTRAINTS.md` requires each off-device destination to be approved in `DECISIONS.md`. D-004 covers the local mock only. The real CCTV server still needs its own entry.
- Backup is off and verified on both devices: `ALLOW_BACKUP` absent from package flags. Device-transfer behavior itself was not exercised.
- `THREAT.md` bars real images going to a real server until server-side deletion exists, so plan step 8 stays mock-only.

Next:
- Owner: notice text sign-off before any live-face run, named test person, legal basis, `CONSTRAINTS.md` review.
- Owner: fix GitHub billing so CI can run.
- Audit ML Kit telemetry traffic (`transport-backend-cct`), see `THREAT.md`.
- Build the screens in design order: connect, sign in, person, consent, capture, review, upload. Design: https://claude.ai/artifact/Lb5vSuHUeweB1KDiGdHGfD
- Owner's real-face test comes last, then thresholds are tuned.

Do not touch:
- Nothing protected in code yet. See `CONSTRAINTS.md`.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`.

## Earlier session: 2026-10-31, morning (model: Claude Opus 5.5, claude-opus-5-5)

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
