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
