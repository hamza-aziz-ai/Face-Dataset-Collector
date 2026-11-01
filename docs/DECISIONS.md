# DECISIONS

One entry per meaningful decision. Newest last.

## D-001: Recreate the Gradle build on latest stable versions

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5), reviewed by Codex gpt-6.1-sol.
Context: `gradle/libs.versions.toml` and the wrapper were never committed and no copy exists, so the original versions are lost.
Options:

1. Guess versions from July 2025, when the project started.
2. Latest stable of everything.
3. Include prereleases.

Chosen: 2, as the owner directed.
Why: nothing to preserve, since the original versions are unknown. Prereleases are unsafe for a Lane B app.
Trade-off: `minSdk` rises from 21 to 23 because AndroidX requires it, so Android 5.x devices are dropped. `tools:overrideLibrary` was rejected because it risks runtime crashes. AGP 9 has Kotlin support built in, so the `org.jetbrains.kotlin.android` plugin and `kotlinOptions` were removed. `compileSdk` is 37; `targetSdk` stays 36 because no test device runs 37.
Also: removed unused `camera-extensions`. `/gradle/` is no longer ignored. The wrapper pins the distribution with `distributionSha256Sum`.
Revisit if: a target device needs an API level below 23.

## D-002: Hold captured images in memory only

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5), agreed with Codex gpt-6.1-sol.
Context: captured faces must not reach the gallery or backups, and must be deletable.
Options:

1. App-private `cacheDir` session folder.
2. `noBackupFilesDir`.
3. JPEG bytes in a ViewModel, in memory.

Chosen: 3.
Why: no file means no crash leftovers, no cache eviction, no disk cleanup, and no encryption-at-rest question.
Trade-off: process death loses the batch, so staff retake. Clearing references is not secure erasure. Memory limits must cover JPEG bytes, decoded previews, and conversion buffers.
Revisit if: real field use needs uploads to survive process death.

## D-003: Store the `ImageAnalysis` frame that passed the checks

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5), agreed with Codex gpt-6.1-sol.
Context: the frame judged good must be the frame stored.
Options:

1. Accepted analysis frame.
2. `ImageCapture` after a good result.
3. `ImageCapture` with validation of the result.

Chosen: 1.
Why: one use case, no shutter lag, and no gap in which the person moves between check and capture. A capture request produces a different frame.
Trade-off: analysis frames may have less detail than `ImageCapture`. CCTV needs pupils at least 28 px apart and the app aims for 60 px, measured on both devices.
Revisit if: measured face detail falls short. Then use option 3.

## D-004: Approve only the local mock as an off-device destination

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5), agreed with Codex gpt-6.1-sol.
Context: `CONSTRAINTS.md` requires each off-device destination to be approved here. The owner approved building against a mock on 2026-10-31.
Options:

1. In-app fake.
2. Small local server plus real HTTP client.

Chosen: 2, a small Python server on the development computer over `adb reverse`, loopback only, in debug builds only.
Why: only a real client exercises multipart, Bearer headers, transport failures, polling, and real upload durations.
Trade-off: debug builds need a loopback-scoped cleartext exception. Release builds must exclude it and the mock endpoint.
Not approved: the real CCTV server. It needs its own entry here, and server-side deletion must exist first (`THREAT.md`).
Revisit if: the CCTV enrolment API (item 8c) ships.

## D-005: Ship login and consent before capture

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5), agreed with Codex gpt-6.1-sol. CCTV session accepted the order.
Context: the brief put capture first, but pose and blur calibration need a live face, and consent must exist first.
Options:

1. Brief order with capture unreachable.
2. Login and consent first.

Chosen: 2.
Why: synthetic input cannot validate pose or blur, and debug-only capture is not a consent safeguard.
Trade-off: differs from the brief. No live-face run happens until the owner signs off the notice text.
Revisit if: never, while real faces are involved.

## D-006: Limit automation to CI and a versioned quality policy for now

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5), agreed with Codex gpt-6.1-sol.
Context: the owner asked for CI/CD and DevOps/MLOps automation. The face detector is bundled and pinned by dependency version.
Options:

1. CI plus a versioned policy file.
2. Add deployment and model pipelines now.

Chosen: 1.
Why: nothing is ready to deploy, and this app has no trained model. Training and registry work belongs to the CCTV server.
Trade-off: deployment and release automation are deferred, not done.
Revisit if: the first release candidate exists, or the app gets its own trained model.

## D-007: Block on detection inside a single background analyzer thread

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5). Changes the listener approach agreed with Codex gpt-6.1-sol; same goals.
Context: analysis ran on the main thread. The frame must close exactly once, and teardown must be safe while a frame is in flight.
Options:

1. `Tasks.await` in the analyzer, on one background executor.
2. Task listeners with an explicit executor.

Chosen: 1.
Why: no callback outlives `analyze`, so one `finally` closes the frame and shutting down the executor cannot reject a late listener.
Trade-off: the analyzer thread is blocked during detection. `STRATEGY_KEEP_ONLY_LATEST` drops frames meanwhile, which is the wanted behavior.
Also: brightness is now measured on the face box, not the whole frame, and a detector failure has its own `DetectorFailed` state.
Revisit if: detection needs to run in parallel.

## D-008: Bundle IBM Plex Sans and use one fixed light theme

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5). Owner approved the design and the font.
Context: the app must look the same and keep readable contrast on every device.
Options:

1. Bundle IBM Plex Sans, fixed light theme.
2. System font with dynamic color and dark mode.

Chosen: 1.
Why: dynamic color changes contrast per device, and the design was drawn in one palette.
Trade-off: three font files add about 600 KB. Dark mode is not offered, except the capture screen, which is always dark.
Source: `@ibm/plex-sans` 1.1.0 release from `github.com/IBM/plex`, OFL 1.1, licence shipped in `assets/licenses/`. Weights 400, 500, 600.
Revisit if: staff ask for dark mode.

## D-009: Use OkHttp, refuse redirects, and retry uploads with one key

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5).
Context: the app needs multipart upload, timeouts, and retries against a provisional API, and a wrong destination would send faces to the wrong place.
Options:

1. OkHttp 5.5.1 with `org.json` from the platform.
2. `HttpURLConnection` with hand-written multipart.
3. Retrofit with a JSON library.

Chosen: 1.
Why: multipart and timeouts by hand are bug farms, and Retrofit adds more than a dozen small calls need. OkHttp is maintained by Square and widely used.
Trade-off: one more runtime dependency. `org.json` needs a test-only copy on the JVM.
Also: redirects are refused so photos cannot move to a host staff never saw. Uploads retry only on an unknown outcome or a 5xx, 3 attempts with 1 s and 2 s backoff, always with the same `Idempotency-Key`. Retry and poll limits are not measured yet.
Revisit if: the API grows past a few calls.

## D-010: Add a dark theme that follows the device setting

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5). Owner asked for it. Replaces the fixed light theme in D-008; the font stays.
Context: the phone runs in dark mode, and the owner wants the app to support it.
Options:

1. Follow the device setting, with a designed dark palette.
2. Stay light only.
3. An in-app theme switch.

Chosen: 1.
Why: it respects the choice staff already made on the device, with no new setting to build.
Trade-off: every color must be checked in two themes. Dynamic color stays off, so contrast stays as designed.
Revisit if: staff need to override the device setting.

## D-011: Decode setup QR codes with ZXing core on the app's own camera

Date: 2026-11-01. Model: Claude Opus 5.5 (claude-opus-5-5). Owner chose the library.
Context: setup happens once by QR code with a typed fallback. Camera frames here can show faces, and the ML Kit telemetry audit is still open.
Options:

1. `com.google.zxing:core` 3.5.4 on a CameraX `ImageAnalysis` stream.
2. `com.google.mlkit:barcode-scanning` 17.3.0, bundled.
3. `com.google.android.gms:play-services-code-scanner` 16.1.0.

Chosen: 1.
Why: pure Java with no transitive dependencies and no network code, so frames meet nothing new that could send them anywhere, and the audit does not grow. It also runs on the JVM, so CI tests decoding with generated codes and no camera. Option 2 adds to the unaudited telemetry path. Option 3 needs Google Play services, downloads a module on first use, and cannot match the design.
Dependency: `com.google.zxing:core` 3.5.4, Apache 2.0, maintained by the ZXing project, 610 KB jar. The project is in maintenance mode, last release November 2025.
Trade-off: possibly weaker on angled or poorly lit codes than ML Kit, not measured. A QR frame stream is one more place a camera runs, so only the brightness plane is read, nothing is kept, and `FLAG_SECURE` is on.
Also: a scan connects without a confirm step, and the sign-in screen shows the client and host before any password is typed. The camera now unbinds when its screen leaves, for capture too, because it otherwise stayed open behind later screens.
Revisit if: scans fail in real use, or setup codes become signed.

## D-012: Re-enrol through the normal flow, with new consent

Date: 2026-11-01. Model: Claude Opus 5.5 (claude-opus-5-5). Owner asked for the feature. The consent choice below is the model's, because the contract does not say.
Context: the CCTV contract allows re-enrolling an `enrolled` person, behind an explicit action with a confirm step and a reason. It does not say whether the person must see the notice again.
Options:

1. A dialog on the roster takes the reason, then the normal notice, new consent, capture, review, and upload.
2. The same, but reuse the earlier consent and skip the notice.
3. A separate re-enrol screen and flow.

Chosen: 1.
Why: new photos of a real person are a new capture, so `CONSTRAINTS.md` calls for informed consent each time, and the notice may have changed since. Option 2 also needs a stored consent ID, which the app does not keep. Option 3 repeats screens that already do the job.
Trade-off: one more step for someone who already agreed once.
Also: no reason is preselected, so the reason on record is one staff chose. A rejected re-enrolment says the current enrolment stays in use.
Confirmed by the CCTV session on 2026-11-01 for the provisional 8c contract: 409 `{"error": "enrolment_open", "job_id": ...}` for the open job, and 422 `{"error": "reason_required"}` for an enrolled person with no reason. CCTV also keeps new consent per re-enrolment as its cautious default. That is its engineering call, not an owner ruling, and it is raising the point with the owner alongside the notice and legal basis sign-off.
Revisit if: CCTV or the owner rules that existing consent covers a re-enrolment.
