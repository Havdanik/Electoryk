# ElektorykScan v0.28.0 — RAW exterior opening topology

## Deep-pass finding
v0.27.8/v0.27.9 hardened doorway traversability by requiring directly observed `rawFloor`, but the helper that decided whether a wall was exterior still used the inpainted/final floor mask (`g.isFloor`). That left an evidence-mixing loophole: an interior partition occluded by furniture could be classified as an exterior edge after footprint filling, and exterior-door rules then allowed only one raw-floor side.

## Fix
- Exterior wall evidence used by automatic door/window promotion now comes only from directly observed `rawFloor`.
- Samples a 10–30 cm band on each side of the structural wall so one-cell axis snapping or consolidation error does not lose the real floor.
- Requires repeated support along >=60% of sampled wall positions and allows only <=18% opposite-side leakage/noise.
- Requires at least three longitudinal samples before a wall can receive exterior privileges.
- Pair-gap doors, continuous-wall doors, and geometry-only windows now share this conservative exterior evidence.
- Filled/inpainted floor remains available for room topology and visualization, but can no longer grant relaxed opening-classification privileges.

## Compatibility
No scene schema, dependency, minSdk, asset-library, editor, import/export or sync format changes.
