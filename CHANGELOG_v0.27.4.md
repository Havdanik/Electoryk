# ElektorykScan v0.27.4 — Zero-Churn Texture Projection

- Hardened `WallTextureBaker`, the finalization stage that projects visual keyframes back onto reconstructed wall geometry.
- Replaced per-tile `double[4][3]` final/raw point matrices with one wall-local flat primitive workspace.
- Reused projection quad, corner-depth, ARCore transform and best-quad buffers for all tiles of a wall.
- Reused a single `Choice` result object instead of allocating a new result every time a better keyframe candidate is found.
- Preserved camera incidence, projected-area, continuity, RGB/Depth occlusion validation, fusion transform and texture coverage scoring.
- No scene/project schema change; existing projects remain compatible.
- Structural brace scan: 221 Java sources, 0 mismatches.
- Full Android compile remains environment-blocked before compilation because Gradle 8.2 is not cached and services.gradle.org cannot be resolved here.
