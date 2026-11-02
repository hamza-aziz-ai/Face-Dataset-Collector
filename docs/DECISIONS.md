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

## D-013: Keep ML Kit telemetry and disclose it

Date: 2026-11-01. Model: Claude Opus 5.5 (claude-opus-5-5). Owner chose.
Context: ML Kit brings `transport-backend-cct`, which sends usage metrics to Google. The audit read the local queue on both devices: 131 and 116 events, each at most 163 bytes, no JPEG. Details are in `THREAT.md`. Google documents no opt-out. Removing one manifest entry would stop uploads, but the Google APIs Terms of Service section 3a says "You will not interfere with this monitoring", and the ML Kit terms make the developer responsible for informing users about these metrics.
Options:

1. Keep ML Kit as is, and disclose the metrics in the notice.
2. Remove the `CctBackendFactory` manifest entry, so nothing uploads.
3. Replace ML Kit with a detector that has no telemetry.

Chosen: 1.
Why: the queued data holds no image, landmark, angle, or face box, and option 2 could be read as breaching the terms. That reading is the model's and is not legal advice. Option 3 means a new dependency and new pose logic before the real-face test.
Trade-off: device details and a per-install ID reach Google, so the notice must say so before real enrollees.
Revisit if: a client requires zero traffic to Google, or legal advice says otherwise. A client's own network can block the host with no effect on the app, because detection runs on-device.

## D-014: Sign setup codes before the first outside client

Date: 2026-11-01. Model: Claude Opus 5.5 (claude-opus-5-5). Owner chose.
Context: a setup code is plain text, so a forged code can point a device at another HTTPS server, which then receives the staff password and later photos. The `client_id` check does not stop this, because a forger controls both the code and the server.
Options:

1. Sign codes now.
2. Sign codes before the first outside client.
3. Never sign, and rely on staff reading the host at sign-in.

Chosen: 2.
Why: while the owner hands codes to internal staff, the risk is small. An outside client is where codes start to travel through other people's hands.
Trade-off: internal setup relies on the host shown at sign-in until then.
Must exist before the first outside client: a `signature` field agreed with CCTV, CCTV creating signed codes per client, the public key built into the app, a guarded signing key with a rotation plan, and a rule for the typed address, which carries no signature.
Revisit if: internal codes start to pass through people other than the owner.

## D-015: Owner sign-off for the owner's own real-face test

Date: 2026-11-01. Model: Claude Opus 5.5 (claude-opus-5-5). Owner chose. Meets the D-005 condition for this test only.
Context: D-005 allows no live-face run until the owner signs off the notice text. Legal basis, staff use, and the final notice still need legal advice.
Options:

1. Sign off the owner's own test only.
2. Also sign off internal staff.
3. Change the wording first.

Chosen: 1.
Scope: test person is the owner, on the owner's own face. Legal basis is the owner's own consent. Destination is the mock on this laptop, which checks and discards photos. Re-enrolment records a new consent each time. Notice version `0.2-mock` adds this line: "Google's on-device face detection sends Google technical usage data from this device, such as device model, app version, and timings. It does not send your photos."
Trade-off: no other real person may be captured under this sign-off, including bystanders in view.
Revisit if: anyone else is to be enrolled. That needs a stated legal basis, a final notice, a withdrawal contact, and confirmed retention first.

## D-016: Show tuning numbers on screen only, against the mock

Date: 2026-11-01. Model: Claude Opus 5.5 (claude-opus-5-5). Owner approved the plan.
Context: every threshold in `QualityPolicy.kt` is a first guess, and tuning needs the numbers behind each decision. `DATA.md` bans logging per-person angles, landmarks, and quality scores.
Options:

1. Show live numbers on the capture screen, and log only counts of which check failed.
2. Log the numbers, and relax the `DATA.md` rule for the test.
3. Tune from the prompts alone.

Chosen: 1.
Why: it keeps the data rule intact, and the numbers leave no trace once the screen closes. Prompt counts and timings still show which check holds a pose back.
Trade-off: exact values must be read off the screen, because `FLAG_SECURE` blocks screenshots.
Also: the readout and the tally appear only when `isMock` is true, meaning a debug build on loopback, so a real server never shows them.
Revisit if: tuning needs values the screen cannot show fast enough.

## D-017: Stop work when an enrolment ends, and block decline during a consent call

Date: 2026-11-01. Model: Claude Opus 5.5 (claude-opus-5-5). Found by Codex, by a separate review, and by the Codex PR bot.
Context: a call started for one enrolment could finish after staff left it. A consent answer could open the camera for someone who had declined, a late upload failure could jump a signed-out device to an empty review, and a late photo could reach the next person's capture.
Options:

1. Cancel running work when an enrolment or session ends, and refuse decline and server change while a call is running.
2. Tag every call with a generation number and compare it at each step.
3. Let decline win, and withdraw the consent afterwards.

Chosen: 1.
Why: one cancel in `clearEnrolment` covers every path, a coroutine cancelled during a blocking call stops at its next step, and sign-out also cancels the call on the wire. Option 3 needs `DELETE /consents`, which the app does not call yet.
Trade-off: outside sign-out a blocking call runs to its end, so `busy` stays set until then, and a decline waits for the consent answer. A request the server already received cannot be recalled. After that, staff cancel on the capture screen, and that consent stays on record.
Revisit if: the app gains consent withdrawal.

## D-018: Withdraw consent from the cancel dialogs

Date: 2026-11-01. Model: Claude Opus 5.5 (claude-opus-5-5). Owner chose to build it now.
Context: the contract has `DELETE /api/v1/consents/{id}`, but a person who changed their mind after consent could only reach the contact in the notice.
Options:

1. A `Person withdrew consent` action in the capture and review cancel dialogs.
2. A withdraw action on the roster for any enrolled person.
3. Leave withdrawal to the contact in the notice.

Chosen: 1.
Why: it covers the moment a person changes their mind, using the consent the app still holds. Option 2 needs a consent ID the app does not keep, or a new server lookup.
Trade-off: after an upload has been accepted, withdrawal still goes through the contact in the notice. Photos are cleared before the call, so a failed call keeps no photos, and staff are told to tell their administrator.
Also: on a re-enrolment, the call ends that attempt only, and the existing enrolment stays in service (CCTV, provisional 8c). The dialog and the roster both say so, because staff could otherwise believe the person has left while the old enrolment keeps matching.
Open for the owner, on the sign-off list: whether a person's withdrawal during re-enrolment should also end the old enrolment. It is a legal question.
Revisit if: staff need to withdraw for already enrolled people in the app. Then the contract needs a lookup by person.

## D-019: Stop before each glasses switch, and measure before detecting glasses

Date: 2026-11-02. Model: Claude Opus 5.5 (claude-opus-5-5). Owner found the problem in the real-face test, and chose to measure first.
Context: in a 10-photo set, the only sign of the switch was a small label, so the owner could not tell when glasses should go on. ML Kit face detection has no glasses check.
Options:

1. A full-screen stop before each switch, plus a measured edge score across the nose bridge, shown on the tuning readout.
2. A trained glasses model.
3. A check on the CCTV server.
4. Stop screens only.

Chosen: 1.
Why: the stop screen fixes the reported problem, with the camera closed until staff confirm. The score costs no dependency, and real numbers decide whether a warning can work.
Trade-off: the score decides nothing yet. Rimless frames and heavy eyebrows may not separate, and one face proves little.
Revisit if: with and without glasses clearly separate across several people. Then add a soft warning that never blocks.
