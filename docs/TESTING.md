# TESTING

A change is done only when these pass and the real output was seen.

## Automatic, on every PR

`.github/workflows/ci.yml` runs:

1. `bash tools/ci/check_images.sh`: passes when every image is in `tools/ci/image-allowlist.txt`.
2. Gradle wrapper validation.
3. `./gradlew assembleDebug testDebugUnitTest lintDebug`: passes on `BUILD SUCCESSFUL`.
4. `python3 -m unittest discover -s tools/mock_server`: passes on `OK`.

CI has no camera or device, so it cannot test capture, pose, or blur.

## By hand, on both devices

Devices: CPH2381 API 34 and OPD2203 API 36.

1. Install and launch the debug build, on the tablet in both portrait and landscape.
2. Confirm 0 entries for the app in `adb logcat -b crash`.
3. Confirm the app shows the preview and a quality hint.
4. Run `CameraLifecycleTest` with `am instrument` after `adb install`, not `connectedDebugAndroidTest`, which uninstalls the app and loses the camera permission. Camera permission must already be granted, and `pm grant` is blocked on these OnePlus devices.

Pass for step 4: `OK (1 test)` and 0 crash lines.

## Whole flow, no camera

With the mock running and `adb reverse tcp:8081 tcp:8081` set, run `EnrolmentFlowTest` with `am instrument`. It uses synthetic gradients and composes no screen, so the camera never opens. Pass: `OK (13 tests)`, in about 100 s. Each test starts with `POST /mock/reset`. The test keeps an empty activity open, because these OnePlus devices freeze a process with nothing on screen after about 27 s, and the run then hangs with no failure.

## Re-enrol tap path, no camera

With the mock running and `adb reverse tcp:8081 tcp:8081` set, run `ReEnrolTapPathTest` with `am instrument`. It taps the real screens from connect to the notice, checks that `Continue` waits for a reason and that the notice shows the Google line, then declines, so capture never starts. Pass: `OK (1 test)`.

## Setup QR code, camera on

Opens the back camera. Tell the owner first. Show a code for `{"base_url":"http://127.0.0.1:8081","client_id":"mock-dedicated"}` on another screen, tap `Scan setup QR code`, and point the device at it. Pass: the app reaches sign-in showing the mock client, and `adb shell dumpsys media.camera` shows no active client on that screen.

A code with a different `client_id` must return to the connect screen with a message and store nothing.

`ScreenGalleryTest` with `-e screen connect` or `-e screen scan` checks these layouts with no camera. `roster`, `reenrol`, and `rejected-reenrol` do the same for re-enrolment.

## Real-face test script

For the owner only, on the owner's own face (D-015). Nobody else may be in view. It opens the front camera and auto-capture fires, so the owner must start it knowingly. Do not screenshot it.

Setup:

1. Start the mock, then `adb reverse tcp:8081 tcp:8081`.
2. Install the debug build. Check the camera permission is granted.
3. Stand in even indoor light against a plain wall, with the device at eye level, at about arm's length.

Run on the phone, then on the tablet:

1. Connect, sign in with `enrol.operator`, and select Sample Person A.
2. Read the notice and record consent.
3. Follow the prompts through all 5 poses, or 10 with glasses. The panel on the camera shows live numbers.
4. For each pose, note any prompt that will not clear and the numbers shown. For `Turn your head to the left`, check that turning your own left is what passes. If left and right are swapped, `YAW_SIGN` flips.
5. Review the photos, retake one, and upload.
6. Tap `Re-enrol` on an enrolled person, choose a reason, and repeat with one retake.

Afterwards, read the numbers from logcat, with `adb logcat -d -s Enrolment:D CaptureAnalyzer:D`. `poseMs` and `photoBytes` come per pose, `frames` and `fixes` show what held each pose back, and `uploadMs` and `checkMs` come per upload. Then look at the kept photos on the review screen against the target photo in `features/FEATURE-001-enrolment.md`.

Pass: all poses complete on both devices without fighting the prompts, left and right are correct, and pupil distance on front poses shows at least 60.

## Release build check

Run `./gradlew assembleRelease`, then `aapt2 dump xmltree <apk> --file AndroidManifest.xml`. Pass: no `debuggable`, `usesCleartextTraffic`, or `networkSecurityConfig`, `allowBackup` is false, both backup rule files are set, and the only permissions are `CAMERA`, `INTERNET`, and `ACCESS_NETWORK_STATE`.

Result on 2026-11-01: passed. The APK is 51 MB, with minify off. It is unsigned, so it cannot be installed and has not been run. Plain HTTP and the mock address are refused by `normalizeBaseUrl` when `BuildConfig.DEBUG` is false, which unit tests cover.

## Simulating server trouble

Run `python tools/mock_server/mock_server.py`, then `adb reverse tcp:8081 tcp:8081` and `adb reverse tcp:8082 tcp:8082`. Port 8081 is a client-dedicated sample and 8082 a central sample. Sign in with `enrol.operator` and `mock-only-password`.

Set a failure with `POST /mock/scenario` and `{"scenario": "..."}`. Values: `normal`, `reject`, `server_error`, `slow`, `drop`, `timeout`, `poll_drop`. `poll_drop` cuts the connection on job status, as if the network went away after the 202. `slow` waits 20 s before reading the upload. `timeout` commits the job, then answers after 35 s, past the app's 30 s read timeout, so the app retries with the same key and finds the one job. `POST /mock/reset` restores the sample roster.

Connect with `http://127.0.0.1:8081`, in debug builds only.

Do not screenshot the camera, because it shows a real face. Tell the owner before any run that opens the camera, because auto-capture fires on a real face in view. Check screen text with `uiautomator dump`.
