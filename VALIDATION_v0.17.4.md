# VALIDATION v0.17.4 — WALL GEOMETRY FIX

## Confirmed regressions fixed

1. `WallLineDetector`: one physical wall must not be expanded into neighboring 10 cm grid rows/columns.
2. A 3-cell-thick wall evidence band must collapse to one plan wall instead of parallel duplicates.
3. While the scan-origin Anchor is `PAUSED`, the logical scan origin remains fixed. The scanner no longer rebases accumulated geometry to the moving camera pose.
4. Live status no longer calls an AUTOMATIC Depth stream `RAW PRECISION`; it reports `RAW + DEPTH` when smooth fallback is enabled.
5. Final-output geometry sanity guard rejects the visual “ready” state when long overlapping parallel wall centerlines remain only 8–32 cm apart.

## Local regression run

A standalone Java regression test compiled the patched `WallLineDetector` against a minimal `ApartmentGrid` test double.

Scenarios:
- single wall row -> exactly 1 horizontal wall at the original row;
- three adjacent wall rows representing one thick/noisy wall -> exactly 1 horizontal wall after deduplication.

Result: `WallLineDetector regression OK`.

## Device test required

Android/ARCore device behavior still must be verified on the target Galaxy S22 Ultra because this environment does not contain the Android SDK/ARCore runtime required to build and execute the APK. Test with a fresh scene/project; do not fuse the known-corrupted v0.17.3 scan into the first v0.17.4 verification pass.
