# DEFENSE: Face enrolment app

Draft by Claude Opus 5.5 on 2026-11-01. The guide requires this page in the owner's own words, so the owner must rewrite it before anything ships.

## What it does

Staff at a client organisation use this Android app to enrol people from their roster for face recognition attendance. It guides the person through 5 poses, or 10 with glasses, and uploads the photos to the client's CCTV attendance server.

## How it works

1. Staff set up the device once, by QR code or typed address, and sign in.
2. They pick a person from the roster, show the notice, and record consent.
3. The camera checks each frame on the device and keeps the frame that passes.
4. Photos stay in memory until staff review and upload them.
5. The server accepts them with a 202, the app clears them, and the server runs its own checks.

## Key decisions and why X over Y

- Memory, not files, because a file is something to leak, clean up, and encrypt.
- Keep the judged frame, not a new capture, because the frame checked must be the frame stored.
- On-device detection, because face images should not travel just to be checked.
- Consent before capture, because a safeguard that ships later is not a safeguard.
- Same `Idempotency-Key` on retry, so an unknown outcome cannot create a second job.

More in `ALTERNATIVES.md`.

## Data structures and why

Photos are a map from pose to JPEG bytes. It makes each pose unique, so a retake is one replacement. The session token lives in memory only. The server address and client live in private preferences, which hold no secret. Details are in `DATA.md`.

## How a malicious actor could abuse it

- A forged setup code points a device at another server. Today the only defence is staff reading the host at sign-in, and codes must be signed before outside clients (D-014).
- A staff member enrols someone without consent, or replaces a person's enrolment with another face. Consent and reason are recorded, but nothing compares faces, and server audit is unspecified.
- A person holds up a photo. There is no liveness check, so staff must supervise.

More in `THREAT.md`.

## Where it fails and how we notice

The first failure will be poses that do not pass on real faces, seen by staff during capture. Upload failures show on screen with photos kept. There is no monitoring yet, so nobody learns of a failure unless staff report it. More in `FAILURE.md`.

## What I would change with more time

- Signed setup codes.
- Crash and failure reporting that carries no personal data.
- Uploads that survive process death, if real use needs them.
- A liveness check.
