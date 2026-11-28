# Validation — v0.19.3 Recovery & Consistency

## PASS

- ArSurfaceGridRenderer coarse topology signature added and copied through Stats.
- ArCoreScanActivity blocks Depth collection during a 950 ms tracking-recovery hold.
- Stable-ready now requires TRACKING stability, non-fallback origin, fixed wall count and fixed coarse topology signature for the hold window.
- Semantic live focus can be invalidated independently from persistent entities.
- Mature semantic AABB expansion requires three consistent large-extent observations.
- SemanticSceneTracker targeted Java compile with stubs.
- ArSurfaceGridRenderer targeted Java compile with stubs.
- ArCoreScanActivity source syntax scan: no parser-level Java errors found (Android/ARCore symbols unavailable here).

Full device validation still requires AIDE/Android SDK + physical ARCore device.

## Regression outputs

- Topology signature: 4 cm wall refinement keeps the same signature; ~32 cm move/size change changes it.
- Mature semantic bounds: wardrobe width `1.00 m -> 1.035 m` after one bad large-extent frame, then controlled growth to `1.23 m` after three consistent confirmations.
