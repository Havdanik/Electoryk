# ElektorykScan v0.27.1 — Zero-boxing semantic candidates

## Deep-pass focus
This pass targets the live RGB-semantic + metric-Depth fusion path (`SemanticSceneTracker`), after the previous interior voxel traversal hardening.

## Changes
- Replaced per-inference `HashMap<Long, Candidate>` with a reusable primitive open-addressed `long -> Candidate` table.
- Removed `Long` autoboxing from every semantic Depth sample lookup and component insertion.
- Added touched-slot tracking so only active candidate slots are cleared/recycled between frames.
- Preserved the bounded `Candidate` object pool and robust 256-point reservoirs.
- Reused camera-transform `float[3]` scratch vectors instead of allocating two arrays on every semantic integration.
- Added bounded load-factor growth to keep pathological/noisy semantic masks from degrading into long probe chains.
- No `scene.json` schema change; no project migration required.

## Behaviour intentionally preserved
Connected-component identity, semantic boundary erosion, metric Depth projection, robust bounds/PCA, mature-entity expansion voting, duplicate merging, stable IDs, wall/opening/furniture association gates, and history caps are unchanged.
