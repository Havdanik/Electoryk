# ElektorykScan v0.26.4 — Semantic Fusion Workspace Hardening

- Removed repeated full-frame `boolean[]` + `int[]` allocations from `SemanticPlanFuser.maskBoxes()` while reconstructing visual door/window components across retained semantic records.
- Added grow-only reusable connected-component workspace and active-range clearing, preserving component separation exactly while reducing final-analysis GC spikes on long scans.
- Removed transient point/ray `float[3]` allocations from semantic world-to-camera classification and ray-to-wall projection; scratch vectors are now reused under a serialized `apply()` pass.
- Preserved scene format, opening geometry rules, semantic-frame normalization, stable object IDs, Depth/RANSAC behavior, and all prior editor/export compatibility.
- versionCode 105 / versionName `0.26.4-semantic-fusion-workspace-hardening`.
