# DECISIONS

One entry per meaningful decision. Newest last.

## D-001: Recreate the Gradle build on latest stable versions

Date: 2026-10-31. Model: Claude Opus 5.5 (claude-opus-5-5), reviewed by Codex gpt-6.1-sol.
Context: `gradle/libs.versions.toml` and the wrapper were never committed and no copy exists, so the original versions are lost.
Options: 1) guess versions from July 2025, when the project started. 2) latest stable of everything. 3) include prereleases.
Chosen: 2, as the owner directed.
Why: nothing to preserve, since the original versions are unknown. Prereleases are unsafe for a Lane B app.
Trade-off: `minSdk` rises from 21 to 23 because AndroidX requires it, so Android 5.x devices are dropped. `tools:overrideLibrary` was rejected because it risks runtime crashes. AGP 9 has Kotlin support built in, so the `org.jetbrains.kotlin.android` plugin and `kotlinOptions` were removed. `compileSdk` is 37; `targetSdk` stays 36 because no test device runs 37.
Also: removed unused `camera-extensions`. `/gradle/` is no longer ignored. The wrapper pins the distribution with `distributionSha256Sum`.
Revisit if: a target device needs an API level below 23.
