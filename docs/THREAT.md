# THREAT

Draft 2026-10-31 by Claude Opus 5.5, reviewed with Codex gpt-6.1-sol. It describes the agreed design. Parts marked planned are not built yet.

## What we protect

Face images and consent records of real people, staff credentials, and session tokens. Face images are biometric data, so a leak cannot be undone.

## Entry points

- Front camera frames.
- Staff login form.
- HTTP responses from the enrolment server.
- Local mock server on the development computer, over `adb reverse`.

## Actors

| Actor | Worst realistic abuse | Mitigation | Status |
|---|---|---|---|
| Person being enrolled | Holds up a photo or swaps with someone else | Staff must supervise the whole session. The app has no liveness check. | Known gap |
| Malicious staff member | Enrols someone without consent | Consent screen, audit of subject, staff, time, and notice version. Server authorizes person and job. | Planned, server side unspecified |
| Someone holding an unlocked device | Views captured faces or uses a live session | Images stay in memory, FLAG_SECURE, images and token cleared after a background timeout. | Planned |
| Other apps on the device | Read images from storage or backups | Nothing written to disk or gallery. Backup and device transfer excluded. | Backup done in this PR, rest planned |
| Network attacker | Reads or changes uploads | TLS only in release. Cleartext only in debug, only for loopback. | Planned |
| Developer mistake | Real photos or secrets land in git or CI | CI image allowlist and review. No real photos in tests. It reduces accidents and does not prove absence. | Planned |
| Compromised dependency | Exfiltrates frames | Pinned versions, wrapper checksum, few dependencies. Face detection stays on-device. | Partly done |
| Mock server user | Real credentials or photos linger on a development computer | Fake credentials only, loopback bind, memory-only with expiry, no logging of bodies, credentials, tokens, or identifiers. | Planned |

## Known gaps

- Face detection establishes neither liveness nor identity continuity. One detected face does not mean the same consenting person throughout.
- Clearing references to JPEG bytes in memory is not secure erasure. Bytes can remain in the heap until overwritten.
- An unlocked or debug-enabled device exposes process memory to anyone with `adb` access.
- Legal basis, notice wording, and retention period are owner decisions and are not settled here.
- Server-side deletion and withdrawal do not exist yet. No real person's images go to a real server until they do.
