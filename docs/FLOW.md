# FLOW

How execution travels through the app. App version 1.0 (`versionCode` 1). F0 was checked against branch `feat/connect-sign-in`, and F1 to F3 against commit `481cb7b`.

The app connects to a server and signs staff in, then shows a front camera preview with a quality warning. It does not capture, store, or upload anything yet. The planned enrolment flows are in `features/FEATURE-001-enrolment.md`.

## How to read the diagrams

Flowcharts read top to bottom. Colours are fixed:

- Blue: the way in.
- Green: a code step.
- Amber: a decision.
- Purple: what the person sees.
- Red: an error.
- Grey: a data store or outside service.

Numbers in a diagram match the numbered trace below it.

## F0: Connect and sign in

```mermaid
flowchart TD
    classDef way fill:#dbeafe,stroke:#1d4ed8,color:#111827
    classDef step fill:#dcfce7,stroke:#15803d,color:#111827
    classDef choice fill:#fef3c7,stroke:#b45309,color:#111827
    classDef seen fill:#ede9fe,stroke:#6d28d9,color:#111827
    classDef bad fill:#fee2e2,stroke:#b91c1c,color:#111827
    classDef outside fill:#f3f4f6,stroke:#4b5563,color:#111827
    A["`**1** App opens`"]:::way --> B{"`**2** Server stored?`"}:::choice
    B -->|No| C["`**3** Validate typed address`"]:::step
    C --> D["`**4** server-info, then store client`"]:::outside
    D --> E["`**5** Sign-in screen`"]:::seen
    B -->|Yes| E
    E --> F["`**6** server-info again`"]:::outside
    F --> G{"`**7** Same client?`"}:::choice
    G -->|No| H["`**8** This server has changed`"]:::bad
    G -->|Yes| I["`**9** Login, token in memory`"]:::outside
    I --> J["`**10** Signed in`"]:::seen
```

1. `MainActivity` creates `EnrolmentViewModel`.
2. `ServerStore` holds the address and client, never a token.
3. `normalizeBaseUrl` refuses anything but a plain HTTPS origin. Debug builds also accept loopback HTTP for the mock.
4. The device binds to the client that answers.
5. Shows `Uploading to:` with the client and host, and a `MOCK SERVER` chip on debug loopback.
6. Runs before the password is sent, so a changed server never receives credentials.
7. `isSameClient` compares `client_id`.
8. A full-screen stop. The only way out is setting up again.
9. A wrong password or unreachable server stays on this screen with a message from `messageFor`.
10. Losing the process signs staff out. Sign-in leads to F0b.

## F0b: Select a person and record consent

```mermaid
flowchart TD
    classDef way fill:#dbeafe,stroke:#1d4ed8,color:#111827
    classDef step fill:#dcfce7,stroke:#15803d,color:#111827
    classDef choice fill:#fef3c7,stroke:#b45309,color:#111827
    classDef seen fill:#ede9fe,stroke:#6d28d9,color:#111827
    classDef outside fill:#f3f4f6,stroke:#4b5563,color:#111827
    A["`**1** Search the roster`"]:::way --> B{"`**2** Not enrolled?`"}:::choice
    B -->|Enrolled or checking| C["`**3** Shown, not selectable`"]:::seen
    B -->|Yes| D["`**4** Fetch notice from server`"]:::outside
    D --> E["`**5** Show notice`"]:::seen
    E --> F{"`**6** Person agrees?`"}:::choice
    F -->|Declined| G["`**7** Nothing recorded`"]:::seen
    F -->|Yes| H["`**8** Record consent on server`"]:::outside
    H --> I["`**9** Go to capture`"]:::step
```

1. `search` waits for a pause in typing, and cancels any older search.
2. `PersonRow` allows only `none`.
3. The contract defines no re-enrolment yet.
4. Text and version always come from the server, so each deployment shows its own wording.
5. Rendered natively.
6. Needs the checkbox and a glasses answer.
7. `personDeclined` clears the selection and sends nothing.
8. `POST /api/v1/consents` cites the notice version. The server takes staff from the session.
9. The old preview stands in until the capture flow lands.

A `401` on any of these calls signs staff out and shows `Your session ended. Sign in again.`

## F1: App start and camera permission

```mermaid
flowchart TD
    classDef way fill:#dbeafe,stroke:#1d4ed8,color:#111827
    classDef step fill:#dcfce7,stroke:#15803d,color:#111827
    classDef choice fill:#fef3c7,stroke:#b45309,color:#111827
    classDef seen fill:#ede9fe,stroke:#6d28d9,color:#111827
    A["`**1** App opens`"]:::way --> B["`**2** Ask for camera permission`"]:::step
    B --> C{"`**3** Granted?`"}:::choice
    C -->|No| D["`**4** Camera permission required`"]:::seen
    C -->|Yes| E["`**5** Set up detector, thread, and camera`"]:::step
    E --> F["`**6** Live preview`"]:::seen
```

1. After sign-in, `AppRoot` shows `CameraPreview`.
2. A `LaunchedEffect` requests `CAMERA` on first compose.
3. `cameraPermissionState.status.isGranted` decides the branch.
4. Denied: a static message. There is no retry button or link to Settings yet.
5. Granted: `remember` creates one `FaceDetector`, one single-thread executor, and the `ImageAnalysis` use case, then `bindToLifecycle` binds the front camera.
6. Preview runs and frames start reaching F2.

## F2: Judging one camera frame

```mermaid
flowchart TD
    classDef way fill:#dbeafe,stroke:#1d4ed8,color:#111827
    classDef step fill:#dcfce7,stroke:#15803d,color:#111827
    classDef choice fill:#fef3c7,stroke:#b45309,color:#111827
    classDef seen fill:#ede9fe,stroke:#6d28d9,color:#111827
    classDef bad fill:#fee2e2,stroke:#b91c1c,color:#111827
    classDef outside fill:#f3f4f6,stroke:#4b5563,color:#111827
    A["`**1** CameraX frame, latest only`"]:::way --> B["`**2** FaceQualityAnalyzer on background thread`"]:::step
    B --> C["`**3** ML Kit, on-device`"]:::outside
    C --> D{"`**4** Detection worked?`"}:::choice
    D -->|No| E["`**5** DetectorFailed`"]:::bad
    D -->|Yes| F{"`**6** How many faces?`"}:::choice
    F -->|None| G["`**7** NoFaceDetected`"]:::seen
    F -->|Two or more| H["`**8** MultipleFaces`"]:::seen
    F -->|One| I["`**9** Average luma inside face box`"]:::step
    I --> J{"`**10** Luma within policy?`"}:::choice
    J -->|Below 100| K["`**11** TooDark`"]:::seen
    J -->|Above 220| L["`**12** TooBright`"]:::seen
    J -->|Yes| M["`**13** Good, no banner`"]:::seen
```

1. `STRATEGY_KEEP_ONLY_LATEST` drops frames while one is being judged.
2. `FaceQualityAnalyzer.analyze` runs on the single background thread. Every path closes the `ImageProxy` in one `finally`.
3. `Tasks.await(detector.process(input))` blocks that thread. ML Kit is the bundled model, so detection runs on-device. Its telemetry library is covered in `THREAT.md`.
4. An exception means the check did not happen.
5. Shown as a failure banner, never as "no face".
6. `qualityFor` in `FaceQuality.kt` decides from the face count.
7. Also the result when a box lies fully outside the frame.
8. Exactly one person must be in frame.
9. `uprightToBuffer` converts the box from upright to buffer coordinates, then `averageLuma` reads the Y plane using its row and pixel stride.
10. Limits are in `QualityPolicy.kt`, version 1, and are not calibrated for a face crop yet.
11. Banner asks for more light.
12. Banner asks for less light.
13. The result posts to the main thread and the banner clears.

`TooBlurry` exists but nothing sets it yet.

## F3: Teardown

```mermaid
sequenceDiagram
    participant C as CameraPreview
    participant X as ImageAnalysis
    participant T as Analyzer thread
    participant D as FaceDetector
    C->>X: 1 clearAnalyzer
    C->>T: 2 queue detector close
    C->>T: 3 shutdown
    T-->>T: 4 finish running frame
    T->>D: 5 close
```

1. `DisposableEffect.onDispose` stops new frames.
2. Closing runs on the analyzer thread so it cannot race the running frame.
3. `shutdown` lets queued work finish and accepts nothing new.
4. The frame in progress completes and closes.
5. The detector closes last.

`CameraLifecycleTest` runs this path. Failures would crash the process and fail the test.
