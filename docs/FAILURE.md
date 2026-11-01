# FAILURE

How the app fails, dependency by dependency. Limits marked unmeasured are first guesses. There is no monitoring or crash reporting yet, so today a failure is noticed only by staff seeing a message, or by `adb logcat`.

## Enrolment server

| If it is | What happens | Tested |
|---|---|---|
| Down or unreachable | A message on the current screen. Photos stay in memory on review | Mock `drop` |
| Returning 5xx | 3 attempts with the same key, 1 s and 2 s apart, then back to review with photos intact | Mock `server_error` |
| Slow, under 30 s | `Sending` stays on screen and photos stay until the answer | Mock `slow` |
| Answering after 30 s | The app times out and retries with the same key, which finds the one job | Mock `timeout` |
| Still checking after 60 s | `Still checking`, and the result appears on the roster later | Unit test only |
| Not the same client | Sign-in stops on a full-screen warning before the password is sent | Unit test only |
| Redirecting | Refused, so photos cannot move to another host | Unit test |
| Wrong or empty JSON | A general error message | Unit test |
| Offline after 202 | The photos are safe on the server, so the app shows `Still checking` | Mock `poll_drop` |

## Camera

Denied permission shows a screen pointing to Settings, and on setup the typed address still works. A camera that fails to bind stops capture with a message. Real devices differ here, and only two have been tried.

## ML Kit

A detector failure blocks capture and never reads as a good frame. Detection runs on-device, so no network is needed, and blocking Google's host has no effect on capture.

## The device

- Process death loses the photos and the session, by design (D-002). If the process dies after the server accepted an upload but before the app heard, a retake gets `enrolment_open` until that job finishes.
- These OnePlus devices froze a background process after about 27 s. An upload may stall if staff leave the app, which is why the screen says `Keep the app open`. Unmeasured on real networks.
- After 2 minutes away from the app, photos and the session are cleared.
- Memory holds at most 10 JPEGs, about 2.2 MB at the target size, plus decoded previews. Low-memory devices are not tested.

## Staff and people

- Poses that will not pass in poor light or for some faces are the most likely first failure. Every threshold is a first guess until the real-face test. See `QualityPolicy.kt`.
- A person who cannot hold a pose, such as a wheelchair user at a fixed camera height, has no alternative path yet.
- Rejected sets mean a full retake. Revisit if the rejection rate makes this hurt.

## First limits expected

1. Capture thresholds, on the first real faces.
2. Upload time on slow Wi-Fi: a 10-photo set is about 2.2 MB, with 60 s to write and 30 s to read. Unmeasured.
3. Roster size: 50 per page, with search on the server. Unmeasured with a large client.

## Known test flake

`serverFailure_keepsPhotosAndRetrySucceeds` failed once, right after adb reconnected, in a run 90 s longer than usual. The cause is not proven. It has not been seen since the mock began reading the upload before answering 500, but that is few runs.
