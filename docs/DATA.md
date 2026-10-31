# DATA

Draft 2026-10-31 by Claude Opus 5.5, reviewed with Codex gpt-6.1-sol. It describes the agreed design. Nothing here is built yet except backup exclusion.

## Captured image batch

- Shape: one JPEG `ByteArray` per pose, keyed by pose name: `front`, `chin_up`, `chin_down`, `left_30`, `right_30`, plus `_glasses` variants when glasses are declared. Maximum 10.
- Why this shape: a map keyed by pose makes each pose unique and makes retake a single replacement. Lookup is simple, and order comes from the fixed pose list.
- Access: written by the capture step, read by review and upload. One session at a time.
- Size: per-image and total byte limits are set in the quality policy file, once measured on both devices.
- Location: memory only, held in a ViewModel. Never on disk, in the gallery, or in a backup.
- Written by: the app only.
- Lifetime: cleared on a durable 202, cancel, logout, and background timeout. A failed upload keeps them. Process death loses the batch, so staff retake.
- Trade-off: no persistent file means no leftovers and no disk cleanup, but an upload cannot survive process death.

## Stored frame

The stored image is the `ImageAnalysis` frame that passed the checks, so the frame judged is the frame stored. If its resolution proves too low, use `ImageCapture` and validate that result again, because a capture request produces a different frame.

## Quality policy

Pose ranges, steady duration, minimum face size, blur and brightness thresholds, and a policy version live in one reviewable Kotlin file. No remote configuration.

## Consent record

- Fields: subject, authenticated staff, time, notice version.
- Written by: staff through the consent screen, sent with `POST /consents`. Stored by the server, which takes the staff identity from the session. See `features/FEATURE-001-enrolment.md`.
- Camera permission is not consent.

## Session token

Held in memory only. Cleared on logout and after the background timeout. Never logged.

## Metrics

Aggregate technical timings only, with no subject or session identifiers. Per-person angles, landmarks, and quality scores are face-derived data and are not logged.

## Mock server

`tools/mock_server/mock_server.py` checks photos and discards them at once, so it keeps pose names and a body hash only. Consent rows and jobs are in memory and are lost on restart. Nothing is written to disk. Logs carry method, path, and status only.
