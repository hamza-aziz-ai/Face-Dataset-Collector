# PRE-SHIP GATE

Run on 2026-11-01 by Claude Opus 5.5. The guide's rule: if a box cannot be ticked honestly, it is not ready to ship. **Result: not ready to ship.** 5 pass, 2 partly, 4 fail, and 3 only the owner can answer. That is expected at this stage, and nothing is being shipped.

| # | Item | Status | Why |
|---|---|---|---|
| 1 | I can explain how this works without notes | Owner | Only the owner can answer. Try the whiteboard drill |
| 2 | `Defense.md` is in my own words and current | No | `defense/DEFENSE-enrolment-app.md` is a model draft |
| 3 | I know why this approach over an alternative | Owner | Written in `ALTERNATIVES.md`. The owner must be able to say it |
| 4 | I can name who could abuse it and what stops them | Partly | `THREAT.md` lists actors, but several have gaps: forged codes, staff replacing a face, no liveness, server audit unspecified |
| 5 | I know the first place it will fail and how I would notice | Partly | First failure is known, but there is no monitoring, so noticing relies on staff |
| 6 | I know the main data structures and why | Owner | Written in `DATA.md` |
| 7 | I read the full diff | No | The owner has reviewed results, not diffs |
| 8 | Tests ran and I saw the real output | Pass | Pasted in each PR, and CI ran on `main` |
| 9 | A customer path was tried by hand end to end | No | Needs the real-face test, then the real server |
| 10 | No secrets, keys, or tokens in the code | Pass | Tracked files scanned on 2026-11-01. Only a fake test token and the mock's fake password. CI runs `gitleaks` on every PR |
| 11 | New dependencies are justified and logged | Pass | See the dependency log below |
| 12 | Rollback steps are written and the last good commit is known | Pass, for code | `ROLLBACK.md`. No release has been made yet, so there is no release rollback |
| 13 | Unverified assumptions are cleared or consciously accepted | No | See the list below |
| 14 | `HANDOVER.md` and `DECISIONS.md` are updated | Pass | |

## Assumptions and open questions

Unverified:

- Thresholds in `QualityPolicy.kt`, including `YAW_SIGN`, work on real faces.
- Analysis frames give at least 60 px between pupils on both devices (D-003).
- The readout and tally behave on a live camera.
- `CameraLifecycleTest` still passes after the `CaptureCamera` change. It opens the camera, so it waits for the real-face test.
- Upload timeouts and retry limits suit real networks.
- ZXing reads codes on the tablet, in poor light, and at an angle.
- The app works against the real CCTV server, whose contract is provisional.
- The cause of the one `serverFailure` flake in `FAILURE.md`.
- The release build runs. It builds and its manifest is checked, but it cannot be installed until it is signed.
- A person who cannot hold a pose has a way to enrol.

Open, for the owner:

- Legal basis for staff and clients, final notice, withdrawal contact, and retention.
- Whether disclosing Google's metrics is enough, in a lawyer's view (D-013).
- Who holds the signing keys for the app and for setup codes.
- Whether the server sits on a LAN, which decides if private CA support is needed.

## Dependency log

| Package | Version | Why | Maintainer |
|---|---|---|---|
| AndroidX core, lifecycle, activity, Compose BOM | 1.19.1, 2.11.0, 1.13.0, 2026.09.00 | App and UI | Google |
| CameraX | 1.6.2 | Camera | Google |
| ML Kit face detection | 16.1.7 | On-device detection. Latest release. Brings the telemetry in D-013 | Google |
| Accompanist permissions | 0.37.3 | Camera permission prompt. Latest release, last published April 2025. Replace with the platform API if it stops being maintained | Google |
| OkHttp | 5.5.1 | API client (D-009) | Square |
| ZXing core | 3.5.4 | Setup QR codes (D-011). Maintenance mode | ZXing project |
| gitleaks action | v3.0.0, pinned by SHA | CI secret scan, owner approved 2026-11-01 | Gitleaks project |
| JUnit, AndroidX test, Espresso, mockwebserver3, org.json | 4.13.2, 1.3.0, 3.7.0, 5.5.1, 20260814 | Tests only | Various |

Secrets touched: none. The mock's password is fake and is committed on purpose.

## Whiteboard drill

Twelve questions on page 22 of the guide, answered aloud with no notes. Ten confident answers means Lane B ready. Only the owner can take it.
