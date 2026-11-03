# HANDOVER

Newest entry on top. Read this first every session and update it last.

## Latest session: 2026-11-03, CCTV 8c client (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- CI tells CCTV after a green `main` (PR #32), and only warns if CCTV refuses (PR #33). The owner's re-saved `SUBMODULES_TOKEN` works: CCTV ran `submodules` from the dispatch.
- The client now follows the 8c API on CCTV `main`: sign-in at `/api/auth/token`, plain-English problems with a null pose for whole-set problems, 2,000,000 bytes per photo, and a 480 px short-side guard.
- Staff register people by organisation ID, with a confirm step that shows the ID large (D-023). The roster shows the ID large and the name beneath.
- CI saves minutes: docs-only changes skip the build, a newer PR push cancels the older run, and the secret scan moved to `secrets.yml` so it still covers docs.
- First run against the real 8c server over adb, the owner's face only (D-024): a duplicate ID was rejected, and a `hamza_aziz` re-enrolment was accepted and published, then rolled back to v4 by CCTV at the owner's choice. CCTV removed its temporary site name, account, and test rows.
- Notice sections now have a fixed gap. The owner approved a shorter notice 1.1, sent to CCTV for its site file, so it fits one tablet screen in both orientations.
- CCTV's site notice 1.0 is based on the mock's notice text. CCTV flagged to the owner that 30-day deletion is not automated yet and that legal advice is still needed.

In progress:
- Nothing.

Broken or risky:
- Nothing checks a typed ID against a real ID card. A wrong ID enrols photos under it, and nothing in the app can rename a person.
- Only one real-server run, on one device, with one face. The phone has not been tried.
- Debug builds trust user-installed CAs (D-024). The owner removed Caddy's certificate from the tablet on 2026-11-03, and CCTV removed its test site name, account, and tunnel.
- Retaking cannot fix `matches someone already enrolled`, but the app offers it. CCTV agreed to add a `code` to each problem; the app will use it once it is on CCTV main.
- The release keystore and its password file may still be in `~/.ssh`. Move both to the key drives.
- The tablet now has the debug app. Uninstall it before installing a release build.

Next:
- Use the problem `code` from CCTV once it is on main.
- See notice 1.1 in the app at the next real-server run. CCTV serves it since 2026-11-03, but the test route was removed before the app read it. The gallery `consent` screen shows the same section lengths fitting.
- Codex review of PR #25 onward from 2026-11-04.
- Glasses warning check with the glasses-off set taken on purpose with glasses on.

## Earlier session: 2026-11-02, glasses warning (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- Owner's camera session on CPH2381 and OPD2203, glasses sets, against the mock. Retake by hand works. Tablet real capture works in portrait and landscape.
- `bridge` without glasses 1.5 to 2.1, with glasses 2.3 to 4.4. Front pitch +1 to +10, chin up 18.6 to 30, chin down -9.6 to -16.3.
- Release builds are signed by hand from the owner's keystore (D-022). The first signed release ran on OPD2203: QR-only setup, `k1` codes accepted, unsigned codes refused.
- The owner created the signing key offline and shared the public part, now `k1` in `SetupKeys.kt`. A code the owner signed scanned as genuine on the tablet, and a changed copy was refused.
- Review warns when 3 of 5 photos in a half look wrong for glasses, with a cutoff of 2.4 (D-021). It never blocks upload. `QualityPolicy.VERSION` is now 2.

In progress:
- Nothing.

Broken or risky:
- The glasses warning rests on one face and one pair of glasses. Rimless frames and other light are untested.
- On the tablet, the no-glasses set was in landscape and the glasses set in portrait, so orientation and glasses are mixed in its numbers.
- Front pitch passed at +7 to +10 on the phone, against a limit of 10, which explains the frequent `Lower your chin a little`. Left as it is, since it passes.
- The release keystore and its password file were in `~/.ssh` on the laptop at first build. Move both to the key drives, as the setup key was.
- `SetupKeys.kt` now holds the owner's key `k1`. The private key must stay offline, never in this repo or with an AI tool.
- Codex and its PR bot have not reviewed PR #25 onward. Their limit resets 2026-11-04.
- `Only one person should be in view` fired on 3 frames across earlier runs, cause unknown.

Next:
- Codex review of PR #25 onward from 2026-11-04.
- Staff sign-off: legal basis, final notice, withdrawal contact, retention, and whether a withdrawal during re-enrolment also ends the old enrolment.
- Release build: signing, plus private CA if the server sits on a LAN.

Do not touch:
- `QualityPolicy.kt` angle and framing values, until more than one face is measured.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`, `SETUP-CODES.md`.

## Earlier session: 2026-11-02, signed setup codes (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- Setup codes are signed with ECDSA P-256, using an offline key the owner holds (D-020). The code signs the address, client ID, and client name, and staff see the signed name. Steps are in `SETUP-CODES.md`.
- Release builds accept signed codes only, with no typed address. Debug builds keep typing and unsigned mock codes.
- `tools/setup_codes/` signs codes offline. A fixture signed by that tool verifies in JVM tests and on CPH2381.
- Review shows the numbers behind each photo against the mock, so tuning no longer depends on reading a moving panel.

In progress:
- Nothing.

Broken or risky:
- `SetupKeys.kt` is empty, so a release build cannot be set up until the owner creates the key and shares the public part. Never put the private key in this repo or give it to an AI tool.
- Codes never expire and work on any device. A rogue client's genuine code can point another client's device at it, though staff then see the rogue's signed name and address.
- Glasses are not detected. `bridge` and `pitch` numbers are still needed, so the warning and tuning are blocked on the next camera session.
- Retake by hand is unconfirmed. The tablet has no real capture yet and lacks recent builds.
- Codex and its PR bot have not reviewed PR #25 onward. Their limit resets 2026-11-04. Independent reviews covered PR #25 and the signing PR.
- `Only one person should be in view` fired on 3 frames across the runs, cause unknown.

Next:
- Owner's short camera session: 10 photos to the review screen, read `bridge` and `pitch`, do one retake, and repeat on the tablet.
- Owner creates the signing key and shares the public key.
- Staff sign-off: legal basis, final notice, withdrawal contact, retention, and whether a withdrawal during re-enrolment also ends the old enrolment.
- Release build: signing, plus private CA if the server sits on a LAN.

Do not touch:
- `QualityPolicy.kt` values, until the numbers above exist.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`, `SETUP-CODES.md`.

## Earlier session: 2026-11-02, first real-face runs (model: Claude Opus 5.5, claude-opus-5-5)

Done:
- Owner ran capture on CPH2381 on their own face (D-015). `CameraLifecycleTest` passed first: `OK (1 test)`, camera closed, 0 images on disk.
- Owner completed all 3 sample people through upload, including Sample Person B's re-enrolment: 3 uploads reached the mock after the last reset, and all 3 show enrolled, with 0 crash lines. A settled 5-pose set took about 24 s. Most poses took 2.5 to 4 s. Upload of 5 photos took 171 to 206 ms on the mock.
- Owner confirmed left and right are correct, so `YAW_SIGN` stays. Owner saw the photos on review and they looked right.
- Glasses stop screen added, after the owner could not tell when to switch (D-019). `bridge` score added to the readout.

In progress:
- Nothing.

Broken or risky:
- Glasses are not detected. The owner took the glasses set with glasses off and the app accepted it. No `bridge` numbers were collected, so a warning cannot be decided yet.
- `front` is the slowest pose: `Lower your chin a little` held it for 49 of 67 frames in run 2. Pitch was not read, so `STRAIGHT_LIMIT` is unchanged.
- Retake by hand is unconfirmed.
- Not run on the tablet: no real capture, and the readout and glasses screen are not installed there.
- Photo sizes were 246 to 408 KB, above the 200 to 240 KB target. They fit the 2 MB limit. Measured against one face, on one device.
- `Only one person should be in view` fired on 1 frame in run 1 and 2 frames in a later `left_30`. Cause unknown: a detector glitch, or something in view.
- Codex and its PR bot have not reviewed PR #25 or later. Their limit resets 2026-11-04.

Next:
- Collect `bridge` with glasses off and on, read pitch on `front`, and run the tablet. Tune `QualityPolicy.kt` only from those numbers.
- Staff sign-off: legal basis, final notice, withdrawal contact, retention, and whether a withdrawal during re-enrolment also ends the old enrolment.
- Release build: signing, plus private CA if the server sits on a LAN.

Do not touch:
- `QualityPolicy.kt` values, until the numbers above exist.

Read first: `CONSTRAINTS.md`, `features/FEATURE-001-enrolment.md`, `TESTING.md`.

## Earlier session: 2026-11-01, review findings (model: Claude Opus 5.5, claude-opus-5-5)

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
- Codex hit its usage limit until 2026-11-04, so it never saw PR #25, and its PR bot failed there. An independent review covered that diff. On or after 2026-11-04, comment `@codex review` on a new PR touching this code.
- `CameraLifecycleTest` has not run since `CaptureCamera` changed. Run it first in the real-face test.
- Consent can be withdrawn in the app only before upload (D-018). On a re-enrolment the old enrolment stays in service, and the app says so. Whether it should also end is a legal question for the owner.
- Pre-ship gate: not ready to ship. Setup codes are not signed (D-014). ML Kit metrics reach Google (D-013).

Next:
- Owner's real-face test against the mock, signed off for the owner only (D-015), then threshold tuning. Tell the owner before anything opens the camera. The tablet needs an install tap.
- Staff sign-off: legal basis, final notice, withdrawal contact, retention, and whether a withdrawal during re-enrolment should also end the old enrolment.
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
