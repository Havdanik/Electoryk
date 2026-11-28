# Validation v0.17.5

- `ArSurfaceGridRenderer.java` compiled with Java 8 against minimal Android/ARCore API stubs: PASS.
- `ArCoreScanActivity.java` Java syntax smoke-check: PASS (full Android symbols unavailable in this container, as expected).
- Old detached `LiveScanCoverageView` is no longer attached to the scan UI: PASS.
- AR overlay renderer is created on GL thread and drawn only while Smart Scan is active: PASS.
- Coverage advancement is gated by a NEW accepted Depth frame timestamp, so repeated render frames cannot fake yellow/green coverage: PASS.
- AR plane coverage texture uses cached buffers; polygon point-in-polygon tests do not allocate arrays per pixel/frame: PASS.
- Version: `0.17.5-ar-surface-grid`, versionCode 30.

Full APK assembly was not possible in this container because the project wrapper tries to download Gradle 8.2 and outbound DNS/network is unavailable here. Build in AIDE/Android SDK environment.
