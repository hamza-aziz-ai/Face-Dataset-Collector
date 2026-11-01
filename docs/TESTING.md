# TESTING

A change is done only when these pass and the real output was seen.

## Automatic, on every PR

`.github/workflows/ci.yml` runs:

1. `bash tools/ci/check_images.sh`: passes when every image is in `tools/ci/image-allowlist.txt`.
2. Gradle wrapper validation.
3. `./gradlew assembleDebug testDebugUnitTest lintDebug`: passes on `BUILD SUCCESSFUL`.

CI has no camera or device, so it cannot test capture, pose, or blur.

## By hand, on both devices

Devices: CPH2381 API 34 and OPD2203 API 36.

1. Install and launch the debug build.
2. Confirm 0 entries for the app in `adb logcat -b crash`.
3. Confirm the app shows the preview and a quality hint.
4. Run `CameraLifecycleTest` with `am instrument` after `adb install`, not `connectedDebugAndroidTest`, which uninstalls the app and loses the camera permission. Camera permission must already be granted, and `pm grant` is blocked on these OnePlus devices.

Pass for step 4: `OK (1 test)` and 0 crash lines.

Do not screenshot the camera preview, because it shows a real face.
