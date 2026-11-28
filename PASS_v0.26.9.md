# ElektorykScan v0.26.9 — Zero-churn Live Guidance

Deep scanner-first stability pass.

- LiveScanCoverage no longer allocates one `int[2]` object for every interior gap on every UI snapshot. The bounded 1200-cell guidance scan now writes missing-cell coordinates into reusable primitive X/Z workspaces and passes only an active count to target selection.
- ArCoreDepthCollector direction coverage now reuses its existing forward-axis scratch vectors instead of allocating two small arrays for every direction update.
- PlaneEvidenceTracker now rotates the canonical plane N/U/V axes into reusable scratch vectors. This removes nine float values plus array headers per accepted ARCore plane update without changing plane classification or metric geometry.
- Hole count, 50-cell visualization cap, nearest-target selection, wall/floor/ceiling evidence, and scene/project formats remain behavior-compatible.
- No scene.json migration required.
