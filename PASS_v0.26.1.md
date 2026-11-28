# ElektorykScan v0.26.1 — RANSAC available-set hardening

- DepthSurfaceTracker RANSAC now samples only points still available after earlier plane extraction passes. Previously later passes sampled the full original set and discarded already-consumed points, reducing effective RANSAC iterations and making secondary walls/floor/ceiling easier to miss.
- Added grow-only reusable available-index workspace; avoids rebuilding boxed collections and remains AIDE/Java compatible.
- Reused the per-frame boolean availability mask instead of allocating it every accepted depth evidence frame.
- Removed tiny but high-frequency float-array allocations from mature-surface reacquisition and duplicate-plane consolidation. Corner projection now writes into synchronized tracker scratch buffers.
- No scene.json schema change; no project migration required.
