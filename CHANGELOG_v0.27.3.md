# ElektorykScan v0.27.3 — Primitive Live Cloud Ingestion

- Replaced `VoxelPointCloud` `HashMap<Long, Acc>` with a primitive open-addressed `long -> Acc` table. Depth ingestion no longer boxes a `Long` or allocates a HashMap node for every new voxel.
- Preserved weighted sub-voxel means, raw/smoothed weighting, independent-frame counting, observer baseline/parallax confirmation, confidence calculation, checkpoint snapshots and the configured `maxVoxels` cap.
- Replaced the remaining `LiveScanCoverage` 2D `HashMap<Long, Cell>` with a bounded primitive `long -> Cell` table. Camera visits, accepted Depth endpoints, gap detection, weak-zone targeting and snapshot traversal now avoid boxed lookup keys.
- The 3D live coverage table remains primitive as before; this pass makes both 2D guidance and persistent reconstruction cloud ingestion primitive-key paths.
- No `scene.json` schema change and no project migration required.
