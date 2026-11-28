# ElektorykScan 0.24.9 — Fused Center Snapshot Integrity

Scanner correctness pass focused on the semantic tracker → Digital Twin handoff.

## Fixed
- `SemanticSceneTracker.snapshotEntities()` now preserves `fusedCenterX/Y/Z` and `fusedCenterValid` in detached snapshots.
- v0.24.8 learned a robust multi-view centroid internally, but the copy helper silently dropped it. Downstream promotion therefore fell back to the historical union-AABB center, partially defeating the previous scanner fix.
- The robust centroid now survives the tracker snapshot boundary and is used by `SemanticFurniturePromoter` / downstream consumers through `SemanticEntity.centerX/Y/Z()`.
- No scene schema migration is required; persisted semantic entities remain backward compatible.

## Version
- versionCode 90
- versionName `0.24.9-fused-center-snapshot-integrity`
