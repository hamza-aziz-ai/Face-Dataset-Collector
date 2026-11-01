# FLOW

How execution travels through the app. Checked against commit `481cb7b` on `perf/ml-kit-background`, app version 1.0 (`versionCode` 1).

Today the app is one screen: a front camera preview with a quality warning. It does not capture, store, or upload anything. The planned enrolment flows are in `features/FEATURE-001-enrolment.md`.

## How to read the diagrams

Flowcharts read top to bottom. Colours are fixed:

- Blue: the way in.
- Green: a code step.
- Amber: a decision.
- Purple: what the person sees.
- Red: an error.
- Grey: a data store or outside service.

Numbers in a diagram match the numbered trace below it.

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

1. `MainActivity.onCreate` sets the Compose content with `CameraPreview`.
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
