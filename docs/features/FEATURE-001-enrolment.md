# FEATURE-001: Guided enrolment capture

Goal: staff enrol a person from the client's roster by capturing 5 poses, or 10 with glasses, and uploading them to the CCTV Face Recognition Attendance System.

Status: in progress, against a mock server. The real API is CCTV item 8c and is not built.

## Provisional contract

Source: CCTV session, 2026-10-31. Provisional. Items 1 and 6 still need owner sign-off on policy.

The tenant always comes from the staff login, never from a request field (CCTV D-036). Roles: `client_admin`, `enrolment_operator`, `viewer`. Only the first two may enrol.

- `POST /api/v1/auth/login` with `{username, password}` returns `{token}`.
- `GET /persons?query=` lists the login's client roster. The operator selects a person; IDs are never typed. A cross-client ID returns 404.
- `POST /consents` with `{person_id, notice_version, captured_at, method: "staff_attested_in_app"}` returns 201 `{consent_id}`. The server records staff from the session.
- `DELETE /consents/{id}` withdraws consent. The mock returns 202 `{deletion_id}` until CCTV item 8e exists.
- `POST /api/v1/enrolments` uses a Bearer token and multipart: `person_id`, `consent_id`, one image per pose. Header `Idempotency-Key` is one UUIDv4 per attempt. It returns 202 `{job_id}`.
- Same key and same body returns the original job. Same key and different body returns 409. After an ambiguous timeout, retry with the same key and bounded backoff. An upload without active consent for that person and client returns 422.
- `GET /api/v1/enrolments/{job_id}` returns `pending | accepted | rejected` plus per-photo problems. This replaces the earlier `queued|checking|published|rejected`.

202 is durable: every photo is stored and the job committed, so the app discards local images on 202. A rejected job means a retake under a new key.

## Pose set

Required, each exactly once: `front`, `chin_up`, `chin_down`, `left_30`, `right_30`. Optional, all or none: the 5 `<pose>_glasses` photos. Anything else is rejected.

Limits: JPEG, at most 2 MB each, short side at least 480 px, 10 photos per job. The server checks exactly 1 face, detection confidence at least 0.7, and re-checks pose and blur.

Face detail: pupils at least 28 px apart in the received image. The app aims for 60 px or more.

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
