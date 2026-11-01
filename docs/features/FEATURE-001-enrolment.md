# FEATURE-001: Guided enrolment capture

Goal: staff enrol a person from the client's roster by capturing 5 poses, or 10 with glasses, and uploading them to the CCTV Face Recognition Attendance System.

Status: in progress, against a mock server. The real API is CCTV item 8c and is not built.

## Provisional contract

Source: CCTV session, 2026-10-31. Provisional. Items 1 and 6 still need owner sign-off on policy.

The tenant always comes from the staff login, never from a request field (CCTV D-036). Roles: `client_admin`, `enrolment_operator`, `viewer`. Only the first two may enrol.

- `POST /api/v1/auth/login` with `{username, password}` returns `{token}`.
- `GET /api/v1/persons?query=&limit=50&cursor=` lists the login's client roster and returns `{persons: [{person_id, name, employee_code, department?, enrolment_status}], next_cursor}`. `enrolment_status` is `none | pending | enrolled`. The operator selects a person; IDs are never typed. A cross-client ID returns 404.
- `POST /api/v1/consents` with `{person_id, notice_version, captured_at, method: "staff_attested_in_app"}` returns 201 `{consent_id}`. The server records staff from the session.
- `DELETE /api/v1/consents/{id}` withdraws consent. The mock returns 202 `{deletion_id}` until CCTV item 8e exists.
- `POST /api/v1/enrolments` uses a Bearer token and multipart: `person_id`, `consent_id`, one image per pose. Header `Idempotency-Key` is one UUIDv4 per attempt. It returns 202 `{job_id}`.
- Same key and same body returns the original job. Same key and different body returns 409. After an ambiguous timeout, retry with the same key and bounded backoff. An upload without active consent for that person and client returns 422.
- `GET /api/v1/enrolments/{job_id}` returns `pending | accepted | rejected` plus per-photo problems. This replaces the earlier `queued|checking|published|rejected`.

202 is durable: every photo is stored and the job committed, so the app discards local images on 202. A rejected job means a retake under a new key.

## Server and storage

Source: CCTV session, 2026-10-31. Owner decisions, relayed by that session: no certificate pinning for now, with normal HTTPS validation and a private CA only for the dedicated LAN case. One server-side encryption key for now, with per-client keys a gap to close before a real client. Photos, vectors, and crops are deleted 30 days after withdrawal or leaving. Each client supplies its own withdrawal contact, which the app shows as served.

- The API host differs per client, so the app hardcodes none. Client-dedicated: the client's own server. Centralized: one HTTPS hostname per client.
- Setup happens once, by QR code with a typed fallback. Payload: `{base_url, client_id}`.
- `GET /api/v1/server-info` returns `{client_id, client_name, notice_version, notice_url}`. If `client_id` differs from the stored one, the app refuses and asks for setup again.
- HTTPS only. A private CA is allowed only for the dedicated LAN case.
- The enrolment contract is identical in both options. Storage is the server's concern: one bucket per client, with a separate encryption key per client.
- The app fetches the notice from `notice_url` and never hardcodes it. Format: `{version, effective_date, controller, processor?, contact: {name, email, phone?}, sections: [{heading, body}]}`. `processor` appears on central servers only. The app renders it natively, and a consent cites `version`.
- Staff see `Uploading to: <client_name> (<host>)` at sign in and capture, and a full-screen warning if the stored client or host changes.

## Rejected jobs

A rejection means retaking the full set under a new `Idempotency-Key`, because the app clears photos on 202 and the server deletes a rejected set. CCTV confirmed this for v1. Revisit only if a measured rejection rate makes retakes hurt; then add `retake_of: <job_id>` with a short server-side hold.

The new job cites the same `consent_id`. Consent stays valid across retakes until it is withdrawn, the person leaves, or its notice version is superseded.

## Re-enrolment

Source: CCTV session, 2026-10-31. Built against the mock (D-012).

- Allowed for `enrolled`, behind an explicit `Re-enrol` action with a confirm step and a reason: `appearance_changed | poor_photos | other`. Roles: `client_admin`, `enrolment_operator`.
- Same `POST /api/v1/enrolments`, plus an optional `reason` field. The server treats the job as a replacement.
- The old vectors stay in service until the new job is accepted. A rejected or expired re-enrolment leaves the old enrolment untouched.
- `pending` stays blocked: one open job per person. The server returns 409 with the open `job_id`.
- Each re-enrolment shows the notice and records a new consent (D-012). CCTV agreed on 2026-11-01 as a cautious default. Owner ruling pending.
- Error codes, confirmed by CCTV on 2026-11-01: 409 `{"error": "enrolment_open", "job_id": ...}`, and 422 `{"error": "reason_required"}` for an enrolled person with no reason.
- An expired job returns the person to their previous status: `none` or `enrolled`.

## Pose set

Required, each exactly once: `front`, `chin_up`, `chin_down`, `left_30`, `right_30`. Optional, all or none: the 5 `<pose>_glasses` photos. Anything else is rejected.

Limits: JPEG, at most 2 MB each, short side at least 480 px, 10 photos per job. Aspect ratio does not matter to the server. The server checks exactly 1 face, detection confidence at least 0.7, and re-checks pose and blur.

Face detail: pupils at least 28 px apart in the received image. The app aims for 60 px or more.

## Target photo

Measured from the owner's reference set in the CCTV project (`FACIAL-RECOGNITION-DATASET/hamza_aziz`). Those photos stay there. Never copy them into this repo. These are numbers from a single person's set, so calibrate them on real runs.

- Portrait 4:5 crop, about 1150 x 1440, 200 to 240 KB per JPEG, with no EXIF rotation.
- Head and shoulders, with space above the hair and the collar in frame.
- Plain light wall, even indoor light, one person, neutral expression.
- Front pose: pupils about 24% of the image width apart, or about 275 px in the reference.
- `left_30` is the person's own left, so the nose points towards image right. `right_30` is the opposite. Stored photos are not mirrored.
- Turned poses keep both eyes visible.
- `chin_down`: the person looks down and eyelids drop, so an eyes-open check must not apply to this pose.
- Glasses: normal wear, eyes visible through the lenses.

What this means for the app:

- An about 1080p analysis frame gives a 1080 x 1350 crop, close to the reference and far above the 60 px pupil target.
- Ten JPEGs at this size total about 2.2 MB, which supports holding them in memory (D-002).

## Expiry

A rejected job's photos are deleted at once and only reasons are kept. A job pending after 24 h expires and its photos are deleted. Withdrawal removes photos, vectors, dynamic crops, and the display image.

## Plan

Agreed with Codex on 2026-10-31. Login and consent ship before capture.

1. Restore build.
2. `THREAT.md`, `DATA.md`, backup exclusions.
3. CI.
4. ML Kit off the main thread.
5. Login, person selection, consent, local mock.
6. Guided capture with cancel and delete.
7. Review and retake.
8. Upload with bounded polling.

## Log

- 2026-10-31: plan agreed, contract received from CCTV.
- 2026-10-31: capture and review screens built. Not yet exercised end to end with deliberate poses.
- 2026-10-31: during a device check, a real face was in view of the phone and the app likely auto-captured it. Photos stayed in memory and were cleared on cancel. A file audit found no images on either device, and the mock logged no uploads.
- 2026-10-31: roster and consent screens built.
- 2026-10-31: connect and sign-in built and checked on the phone against the mock. QR setup not built yet.
- 2026-11-01: re-enrol built against the mock (D-012).
- 2026-11-01: QR setup built, with ZXing (D-011). Typed address stays as the fallback.
- 2026-10-31: storage design and full-retake rule received from CCTV. UI design drafted: https://claude.ai/artifact/Lb5vSuHUeweB1KDiGdHGfD
