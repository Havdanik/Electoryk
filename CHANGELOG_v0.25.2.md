# ElektorykScan v0.25.2 — Partial-view center stability

- versionCode 93 / versionName `0.25.2-partial-view-center-stability`.
- Fixed persistent object-position drift during partial occlusion: a robust per-view centroid is still biased toward the visible half of a sofa/bed/table/wardrobe.
- Center fusion now estimates footprint coverage in the persistent oriented coordinate frame and strongly reduces centroid update weight for mature partial views.
- Full views and early tracks remain responsive; large jumps keep the existing outlier attenuation.
- Complements v0.25.0 dimension fusion and v0.25.1 axis stability so ID, center, yaw and dimensions are fused consistently.
- No scene-schema change; backward compatible.
