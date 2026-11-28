# ElektorykScan v0.27.9 — Opening Evidence Unification

- Hardened the older split-wall door detector to use directly observed `rawFloor` instead of inpainted floor.
- Unified door traversability rules across both opening pipelines: an interior crossing now requires observed floor on both sides; exterior openings may use one observed interior side.
- Replaced bounding-grid-only exterior inference for evidence gating with floor-edge evidence from the actual wall runs.
- A scan-path crossing is now a confidence boost only after physical traversability is established, preventing furniture occlusions and slightly misplaced reconstructed walls from becoming doors.
- Kept scene/project serialization unchanged.
