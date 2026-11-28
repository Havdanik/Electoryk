# Validation — v0.19.2 Long Scan Stability

## PASS

- `DepthSurfaceTracker` targeted Java compile.
- Long-wall regression: one noisy physical wall remains one stable track.
- Extreme coplanar outlier does not stretch mature bounds.
- 18 cm relocalization-like plane wobble reacquires canonical mature wall instead of creating a parallel layer.
- One far single-frame patch cannot grow a mature wall.
- Adjacent repeatedly confirmed coverage can still extend the wall.
- `SemanticSceneTracker` targeted Java compile.
- center-label hysteresis keeps WALL after one DOOR flicker.
- repeated nearby DOOR observations deduplicate to one committed semantic entity.
- `ArCoreScanActivity` parsed by javac without Java syntax errors (Android symbols unavailable in this environment).

Full ARCore runtime/device validation still requires AIDE/Android SDK + physical device.
