# ElektorykScan v0.28.7 — Non-finite Depth Quarantine

- versionCode 128 / versionName `0.28.7-nonfinite-depth-quarantine`.
- Added a reconstruction-wide `SceneAnalysisPipeline` quarantine boundary with a zero-copy fast path for healthy scans; corrupt coordinates are removed and invalid confidence is normalized before any downstream geometry stage.
- Hardened `RoomAnalyzer` so NaN/Infinity coordinates are rejected consistently not only by the weighted envelope, but also by boundary histograms and support scoring.
- Non-finite point confidence is now treated as zero evidence instead of propagating NaN through histogram/support math.
- Hardened `InteriorObjectDetector` against null/non-finite cloud points before voxel indexing and AABB accumulation.
- Prevents a single corrupt sensor/fusion sample from poisoning room confidence, object bounds, or producing non-finite `ReviewIssue` values that can break JSON export.
- No scene schema changes; existing projects remain compatible.
