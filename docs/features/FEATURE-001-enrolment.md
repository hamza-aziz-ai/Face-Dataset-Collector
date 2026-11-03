# FEATURE-001: Guided enrolment capture

Goal: staff enrol a person from the client's roster by capturing 5 poses, or 10 with glasses, and uploading them to the CCTV Face Recognition Attendance System.

Status: in progress. The real API, CCTV item 8c, is on CCTV `main` (PRs #103 and #104, 2026-11-03), with its reference in CCTV `docs/features/enrolment-api.md`. The app is checked against the local mock, which follows it.

## Contract

Source: CCTV session, 2026-10-31, updated 2026-11-03 when 8c was built. Items 1 and 6 still need owner sign-off on policy.

The tenant always comes from the staff login, never from a request field (CCTV D-036). Roles: `client_admin`, `enrolment_operator`, `viewer`. Only the first two may enrol.

- `POST /api/auth/token` with `{username, password}` returns `{token, role, expires_at}`. It is outside `/api/v1`, because browsers use the cookie login beside it. Any role gets a token: a viewer gets 403 `role_cannot_enrol` on persons, consents, and enrolments, and 401 always means signed out.
- `GET /api/v1/server-info` and `GET /api/v1/notice` need no login, because they carry nothing about people.
- `GET /api/v1/persons?query=&limit=50&cursor=` lists the login's client roster and returns `{persons: [{person_id, name, employee_code?, department?, enrolment_status}], next_cursor}`. `enrolment_status` is `none | pending | enrolled`. `employee_code` is null unless an admin import set it. A cross-client ID returns 404.
- `POST /api/v1/persons` with `{person_id, name, department?}` registers a person and returns 201 `{person_id}` (D-023). `person_id` is the organisational or institutional ID: a letter or digit first, then up to 63 of `A-Z a-z 0-9 . _ -`, and no Windows device name (`CON`, `PRN`, `AUX`, `NUL`, `COM1`-`9`, `LPT1`-`9`, with or without an extension), because it becomes a folder name. `name` is 1 to 120 characters, trimmed. Errors: 409 `person_exists`, unique in any letter case; 422 `invalid_person_id` or `invalid_name`.
- `POST /api/v1/consents` with `{person_id, notice_version, captured_at, method: "staff_attested_in_app"}` returns 201 `{consent_id}`. The server records staff from the session.
- `DELETE /api/v1/consents/{id}` withdraws consent. The mock returns 202 `{deletion_id}` until CCTV item 8e exists.
- `POST /api/v1/enrolments` uses a Bearer token and multipart: `person_id`, `consent_id`, one image per pose. Header `Idempotency-Key` is one UUIDv4 per attempt. It returns 202 `{job_id}`.
- Same key and same body returns the original job. Same key and different body returns 409. After an ambiguous timeout, retry with the same key and bounded backoff. An upload without active consent for that person and client returns 422.
- `GET /api/v1/enrolments/{job_id}` returns `{status: pending | accepted | rejected, problems: [{pose, problem}]}`. `problem` is plain English, such as "the face is blurred (sharpness 14, below 40)", and the app shows it as it is. `pose` is null for a problem with the whole set, such as a missing photo.
- Errors are `{"error": code}`. Until a site has a notice, notice and consents return 503 `no_notice`. A consent citing an old notice version gets 422 `notice_version_superseded`. The proxy strips `/api`, so the server mounts these routes at `/v1/...`.

202 is durable: every photo is stored and the job committed, so the app discards local images on 202. A rejected job means a retake under a new key.

## Server and storage

Source: CCTV session, 2026-10-31. Owner decisions, relayed by that session: no certificate pinning for now, with normal HTTPS validation and a private CA only for the dedicated LAN case. One server-side encryption key for now, with per-client keys a gap to close before a real client. Photos, vectors, and crops are deleted 30 days after withdrawal or leaving. Each client supplies its own withdrawal contact, which the app shows as served.

- The API host differs per client, so the app hardcodes none. Client-dedicated: the client's own server. Centralized: one HTTPS hostname per client.
- Setup happens once, by signed QR code. Payload: `{base_url, client_id, client_name, kid, sig}`, signed offline by the owner (D-020, `SETUP-CODES.md`). The typed address exists in debug builds only.
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

Limits: JPEG, at most 2,000,000 bytes each, short side at least 480 px, 10 photos per job. Aspect ratio does not matter to the server. The server checks exactly 1 face, detection confidence at least 0.7, face sharpness at least 40 (Laplacian variance of the face scaled to 112 x 112), and each pose against the same set's front photo: the turns at least 0.08 of the eye distance the right way, chin up and down at least 0.04, and front within 0.10. A swapped left and right pair is rejected. These are calibrated on the owner's photos and still provisional. The app's own sharpness check is on a different scale (128 px, minimum 60), so the server's is the one that counts.

The app refuses to keep a photo whose crop is narrower than 480 px, and asks the person to move closer. From an about 1080p frame the narrowest allowed face still gives 672 px in landscape and 840 px in portrait, so this matters only on a device that falls back to a small frame.

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

- 2026-11-03: first run against the real 8c server, the owner's face only (D-024). A duplicate was rejected, and a re-enrolment was accepted, published, and then rolled back by CCTV at the owner's choice. Notice paragraphs now have space between them, and the owner approved a shorter notice 1.1 so it fits one tablet screen.
- 2026-11-03: client moved to the built 8c API: sign-in at `/api/auth/token`, registration by organisation ID (D-023), plain-English problems, a 2,000,000-byte limit, and a 480 px guard.
- 2026-10-31: plan agreed, contract received from CCTV.
- 2026-10-31: capture and review screens built. Not yet exercised end to end with deliberate poses.
- 2026-10-31: during a device check, a real face was in view of the phone and the app likely auto-captured it. Photos stayed in memory and were cleared on cancel. A file audit found no images on either device, and the mock logged no uploads.
- 2026-10-31: roster and consent screens built.
- 2026-10-31: connect and sign-in built and checked on the phone against the mock. QR setup not built yet.
- 2026-11-02: glasses warning on review, from the owner's numbers on both devices (D-021). Retake by hand confirmed, and real capture on the tablet in portrait and landscape.
- 2026-11-02: setup codes signed, typed address removed from release (D-020).
- 2026-11-02: first real-face runs by the owner on CPH2381, all 3 sample people through upload. 5 poses in about 24 s once settled, left and right correct. Glasses step added after the owner found the switch unclear (D-019).
- 2026-11-01: consent withdrawal added to the cancel dialogs (D-018).
- 2026-11-01: owner signed off the notice for the owner's own test only (D-015).
- 2026-11-01: slow-server and late-answer runs pass on both devices, and the light theme is checked on the upload path.
- 2026-11-01: ML Kit telemetry audited and kept (D-013). The notice must disclose Google's metrics before real enrollees.
- 2026-11-01: re-enrol built against the mock (D-012).
- 2026-11-01: QR setup built, with ZXing (D-011). Typed address stays as the fallback.
- 2026-10-31: storage design and full-retake rule received from CCTV. UI design drafted: https://claude.ai/artifact/Lb5vSuHUeweB1KDiGdHGfD
