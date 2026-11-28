# ElektorykScan v0.27.5 — Primitive Surface Tile Map

- Replaced `DepthSurfaceTracker.Track.tiles` `HashMap<Long,Tile>` with a dedicated primitive open-addressed `long -> Tile` table.
- Removes `Long` boxing and `HashMap.Node` allocation from the structural-plane coverage hot path fed by accepted ARCore Depth points.
- Snapshot traversal, track consolidation/reprojection and stale-red-tile cleanup now iterate primitive slots directly.
- Added tombstone-aware deletion and automatic compaction/growth to keep long scans stable after repeated stale-tile cleanup.
- Preserved tile state semantics, independent-frame/baseline/view-angle confirmation, mature-wall sticky bounds, plane matching and architectural classification.
- Project/scene storage format is unchanged; no migration required.
- versionCode 116 / versionName `0.27.5-primitive-surface-tile-map`.
