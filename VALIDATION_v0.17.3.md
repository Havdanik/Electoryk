# v0.17.3 validation

- Pure-Java VoxelPointCloud + LiveScanCoverage compile: PASS.
- Incremental confirmed counter equals final confirmed snapshot size in regression: PASS.
- Live coverage stress: 28,000 unique voxels; average snapshot ~4.8 ms in container JDK test; display payload <= 2,200: PASS.
- Anchor hard gate removed from onDrawFrame: collector runs through logical origin even when Anchor is not TRACKING.
- Stats no longer called synchronously every GL frame; telemetry executor schedules ~600 ms.
- Periodic checkpoint cadence: 60 s.
- versionCode 28; versionName 0.17.3-live-self-heal.

## Structural validation

- Java source files: 200.
- Activities: 39; missing from Manifest: 0.
- XML files parsed: 9; errors: 0.
- ArCoreScanActivity javac syntax smoke: no parse-level errors (Android SDK symbols unavailable in container).
- `collector.stats()` is no longer called synchronously from `onDrawFrame()`.
- `collector.collect()` is no longer inside an `originAnchor TRACKING` hard gate.
