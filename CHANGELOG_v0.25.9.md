# ElektorykScan v0.25.9 — Zero-allocation robust depth

- Removed the remaining per-component quantile allocations from `SemanticSceneTracker.Candidate.finalizeRobustBounds()`.
- Removed repeated projection-array allocations from `extentAtYaw()`.
- Added bounded per-candidate scratch arrays that are reused by the existing 64-entry Candidate pool.
- Preserved original X/Z reservoir pairing: robust axis-aligned quantiles sort scratch copies only, while PCA/oriented projection continues to use the paired source samples.
- No scene schema change; existing projects remain compatible.
