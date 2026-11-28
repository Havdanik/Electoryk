# ElektorykScan v0.27.0 — Interior voxel traversal hardening

- Reworked InteriorObjectDetector occupancy storage from HashMap<Long, Cell> to a primitive open-addressed long->Cell table.
- Removed the duplicate HashSet<Long> used only for connected-component visited state; visited state now lives on each per-pass voxel Cell.
- Reused one ArrayDeque<Cell> across all interior components instead of allocating a queue per component.
- Removed int[][] neighbour-direction traversal and switched to six explicit primitive neighbour probes.
- Preserved existing interior-cluster thresholds, wall/floor/ceiling exclusion, confidence classification, sorting and 30-item review cap.
- No scene.json/schema migration required.
- Target: lower peak heap/GC during post-scan interior-volume analysis on dense scans, especially furniture/ladder/box clutter.
