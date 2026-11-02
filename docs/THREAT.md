# THREAT

Draft 2026-10-31 by Claude Opus 5.5, reviewed with Codex gpt-6.1-sol. It describes the agreed design. Parts marked planned are not built yet.

## What we protect

Face images and consent records of real people, staff credentials, and session tokens. Face images are biometric data, so a leak cannot be undone.

## Entry points

- Front camera frames.
- Back camera frames, on the setup code screen only.
- Scanned setup codes.
- Staff login form.
- HTTP responses from the enrolment server.
- Local mock server on the development computer, over `adb reverse`.
- Fields the server supplies: `client_name` is cleaned and capped, and `notice_url` must resolve to the same server.

## Actors

| Actor | Worst realistic abuse | Mitigation | Status |
|---|---|---|---|
| Person being enrolled | Holds up a photo or swaps with someone else | Staff must supervise the whole session. The app has no liveness check. | Known gap |
| Malicious staff member | Enrols someone without consent | Consent screen, audit of subject, staff, time, and notice version. Server authorizes person and job. | Planned, server side unspecified |
| Malicious staff member, re-enrolling | Replaces a person's enrolment with someone else's face, so attendance is marked falsely | Explicit action, a reason, the notice, and new consent. The old enrolment stays until the new job is accepted. Nothing compares the new face with the old one. | Known gap, server audit unspecified |
| Someone holding an unlocked device | Views captured faces or uses a live session | Images stay in memory, FLAG_SECURE, images and token cleared after a background timeout. | Planned |
| Other apps on the device | Read images from storage or backups | Nothing written to disk or gallery. Backup and device transfer excluded. | Backup done in this PR, rest planned |
| Someone with a forged setup code | Points a device at their own HTTPS server, then receives the staff password and later photos | Release builds accept only codes signed with the owner's offline key, and have no typed address (D-020). `normalizeBaseUrl` allows only a plain HTTPS origin. Sign-in shows the client and full address before a password is typed, and the server cannot make its name long or multi-line enough to hide them. A later client change stops the app. | Built, needs the owner's key before release |
| Network attacker | Reads or changes uploads | TLS only in release. Cleartext only in debug, only for loopback. | Planned |
| Developer mistake | Real photos or secrets land in git or CI | CI image allowlist and review. No real photos in tests. It reduces accidents and does not prove absence. | Planned |
| Compromised dependency | Exfiltrates frames | Pinned versions, wrapper checksum, few dependencies. Face detection stays on-device. | Partly done |
| Mock server user | Real credentials or photos linger on a development computer | Fake credentials only, loopback bind, photos discarded after checks, no logging of bodies, credentials, tokens, or identifiers. | Done in `tools/mock_server` |

## Known gaps

- ML Kit sends usage metrics to Google at `firebaselogging.googleapis.com`, through `transport-backend-cct`, and it is kept on purpose (D-013). Audited 2026-11-01 from the local queue, `databases/com.google.android.datatransport.events`, on both devices, after real capture runs:
  - Found: device model, OS build, country, locale, carrier code, time zone, app package and version, ML Kit version, one per-install ID, event type, frame size and rotation, detector settings, and integer counts and timings.
  - Not found: images, JPEG data, landmarks, face boxes, or head angles. Each event is at most 163 bytes, and the only non-integer value is the 0.1 minimum face size setting.
  - Limits: this read the queue, not network traffic. The format is private, so not every integer has a known meaning, and one small value could be a count of faces. Most events wait for an idle, unmetered moment. Earlier urgent ones were probably sent, because the urgent queue is empty and has a next-send time, and that is an inference.
  - The per-install ID with a timestamp shows when a device ran detection, though not who was in front of it. The notice must disclose these metrics before real enrollees.
- Anyone holding the signing key can forge a code for any server, so a leak needs a new key and a new app build. See `SETUP-CODES.md`.
- Consent can be withdrawn in the app only before upload (D-018). After that, a person uses the contact in the notice.
- Face detection establishes neither liveness nor identity continuity. One detected face does not mean the same consenting person throughout.
- Clearing references to JPEG bytes in memory is not secure erasure. Bytes can remain in the heap until overwritten.
- An unlocked or debug-enabled device exposes process memory to anyone with `adb` access.
- Legal basis, notice wording, and retention period are owner decisions and are not settled here.
- Server-side deletion and withdrawal do not exist yet. No real person's images go to a real server until they do.
