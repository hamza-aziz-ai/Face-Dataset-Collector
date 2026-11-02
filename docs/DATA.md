# DATA

Draft 2026-10-31 by Claude Opus 5.5, reviewed with Codex gpt-6.1-sol. It describes the agreed design. Nothing here is built yet except backup exclusion.

## Captured image batch

- Shape: one JPEG `ByteArray` per pose, keyed by pose name: `front`, `chin_up`, `chin_down`, `left_30`, `right_30`, plus `_glasses` variants when glasses are declared. Maximum 10.
- Why this shape: a map keyed by pose makes each pose unique and makes retake a single replacement. Lookup is simple, and order comes from the fixed pose list.
- Access: written by the capture step, read by review and upload. One session at a time.
- Size: per-image and total byte limits are set in the quality policy file, once measured on both devices.
- Location: memory only, held in a ViewModel. Never on disk, in the gallery, or in a backup.
- Written by: the app only.
- Lifetime: cleared on a durable 202, cancel, logout, and background timeout. The timeout fires while the app is in the background, unless the device has frozen the process or is in deep sleep, in which case it is applied on return. A failed upload keeps them. Process death loses the batch, so staff retake.
- Trade-off: no persistent file means no leftovers and no disk cleanup, but an upload cannot survive process death.

## Nose-bridge score

- Shape: one `Float` per kept photo, keyed by pose, measured on the kept frame (D-021).
- Why: it drives the glasses warning on review, which judges each half of the set together.
- Location: memory only, in the ViewModel beside the photos. Never logged, stored, or sent, because it is a measurement of the person.
- Lifetime: replaced on retake, and cleared whenever the photos clear.

## Stored frame

The stored image is the `ImageAnalysis` frame that passed the checks, so the frame judged is the frame stored. If its resolution proves too low, use `ImageCapture` and validate that result again, because a capture request produces a different frame.

## Setup code frames

The scan screen reads the brightness plane of each back camera frame in memory, to find a QR code. A person in view is not detected, measured, or kept. Each buffer is dropped after that frame, the decoded text is parsed and not stored or logged, and only the resulting server address and client reach preferences. `FLAG_SECURE` covers the preview.

## Quality policy

Pose ranges, steady duration, minimum face size, blur and brightness thresholds, and a policy version live in one reviewable Kotlin file. No remote configuration.

## Consent record

- Fields: subject, authenticated staff, time, notice version.
- Written by: staff through the consent screen, sent with `POST /consents`. Stored by the server, which takes the staff identity from the session. See `features/FEATURE-001-enrolment.md`.
- Withdrawn from the capture and review cancel dialogs, with `DELETE /consents` (D-018).
- A re-enrolment records a new consent. A retake after rejection reuses the current one.
- Camera permission is not consent.

## Session token

Held in memory only. Cleared on logout and after the background timeout. Never logged.

## Metrics

Aggregate technical timings only, with no subject or session identifiers. Per-person angles, landmarks, and quality scores are face-derived data and are not logged. In a debug build on the mock, the capture screen shows them live, review shows them for each kept photo, and only counts of each fixed prompt, pose timings, and photo sizes are logged (D-016).

## Telemetry to Google

ML Kit metrics go to Google (D-013). Contents and limits are in `THREAT.md`. No image or landmark was found. The queue sits in `databases/com.google.android.datatransport.events`, excluded from backup like all app data.

## Mock server

`tools/mock_server/mock_server.py` checks photos and discards them at once, so it keeps pose names, any re-enrol reason, and a body hash only. Consent rows and jobs are in memory and are lost on restart. Nothing is written to disk. Logs carry method, path, and status only.
