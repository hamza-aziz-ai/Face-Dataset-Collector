# ALTERNATIVES

What was rejected and why. Full reasoning is in `DECISIONS.md`.

| Choice | Rejected | Why rejected | Cost of what we chose | Revisit if |
|---|---|---|---|---|
| Photos in memory (D-002) | Files in `cacheDir` or `noBackupFilesDir` | Files leave crash leftovers and raise cleanup and encryption questions | Process death loses the batch | Real use needs uploads to survive process death |
| Keep the analysed frame (D-003) | `ImageCapture` after a good result | A capture request gives a different frame from the one judged | Analysis frames may hold less detail | Measured face detail falls short |
| Local mock only (D-004) | In-app fake | A fake skips multipart, headers, and real transport failures | Debug builds need a loopback cleartext exception | The CCTV API ships |
| Login and consent before capture (D-005) | Capture first, as in the brief | Debug-only capture is not a consent safeguard | Differs from the brief | Never, while real faces are involved |
| CI and a versioned policy (D-006) | Deployment and model pipelines now | Nothing is ready to deploy, and the app trains no model | Release automation is deferred | The first release candidate exists |
| Block on detection on one thread (D-007) | Task listeners | Listeners can outlive the frame and the executor | The analyzer thread waits during detection | Detection must run in parallel |
| Bundled IBM Plex Sans, no dynamic colour (D-008, D-010) | System font and dynamic colour | Contrast would vary by device | About 600 KB, and two palettes to check | Staff need to override the device theme |
| OkHttp, no redirects (D-009) | `HttpURLConnection`, Retrofit | Hand-written multipart is a bug farm, and Retrofit adds more than a few calls need | One more runtime dependency | The API grows past a few calls |
| ZXing for QR (D-011) | ML Kit barcode, Google Code Scanner | Both add Google services, and Code Scanner needs Play services | Possibly weaker on poor or angled codes | Scans fail in real use |
| New consent per re-enrolment (D-012) | Reuse the earlier consent | New photos are new processing, and the app keeps no consent ID | One more step for the person | The owner or CCTV rules otherwise |
| Keep and disclose ML Kit metrics (D-013) | Block uploads, or replace ML Kit | Blocking could breach Google's terms, and replacing means new pose logic | Device details and a per-install ID reach Google | A client needs zero traffic to Google |
| Sign setup codes before outside clients (D-014) | Sign now, or never | Now costs a contract change before it is needed, and never leaves forged codes open | Internal setup relies on the host shown at sign-in | Codes pass through other people's hands |
| Tuning numbers on screen only (D-016) | Log the numbers | `DATA.md` bans logging face-derived values | Values must be read off the screen | Tuning needs values the screen cannot show fast enough |
