# ElektorykScan v0.25.4 — Axis-aware duplicate merge

- Fixed a multi-view geometry regression in `SemanticSceneTracker.mergeEntityInto()`.
- Duplicate semantic tracks no longer average oriented width/depth in incompatible PCA coordinate frames.
- The dropped track's oriented rectangle is projected into the surviving track's local yaw frame before dimension fusion.
- Prevents ~90° PCA ambiguity from swapping width/depth or turning long sofas/desks/beds into square footprints after duplicate consolidation.
- Duplicate orientation fusion is capped so a short-lived track cannot aggressively rotate a mature instance.
- No scene JSON schema change; previous projects remain compatible.
