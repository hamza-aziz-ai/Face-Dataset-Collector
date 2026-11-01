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

With the mock running and `adb reverse tcp:8081 tcp:8081` set, run `EnrolmentFlowTest` with `am instrument`. It uses synthetic gradients and composes no screen, so the camera never opens. Pass: `OK (10 tests)`. Each test starts with `POST /mock/reset`. The test keeps an empty activity open, because these OnePlus devices freeze a process with nothing on screen after about 27 s, and the run then hangs with no failure.

## Setup QR code, camera on

Opens the back camera. Tell the owner first. Show a code for `{"base_url":"http://127.0.0.1:8081","client_id":"mock-dedicated"}` on another screen, tap `Scan setup QR code`, and point the device at it. Pass: the app reaches sign-in showing the mock client, and `adb shell dumpsys media.camera` shows no active client on that screen.

A code with a different `client_id` must return to the connect screen with a message and store nothing.

`ScreenGalleryTest` with `-e screen connect` or `-e screen scan` checks these layouts with no camera. `roster`, `reenrol`, and `rejected-reenrol` do the same for re-enrolment.

## Simulating server trouble

Run `python tools/mock_server/mock_server.py`, then `adb reverse tcp:8081 tcp:8081` and `adb reverse tcp:8082 tcp:8082`. Port 8081 is a client-dedicated sample and 8082 a central sample. Sign in with `enrol.operator` and `mock-only-password`.

Set a failure with `POST /mock/scenario` and `{"scenario": "..."}`. Values: `normal`, `reject`, `server_error`, `slow`, `drop`. `POST /mock/reset` restores the sample roster.

Connect with `http://127.0.0.1:8081`, in debug builds only.

Do not screenshot the camera, because it shows a real face. Tell the owner before any run that opens the camera, because auto-capture fires on a real face in view. Check screen text with `uiautomator dump`.
