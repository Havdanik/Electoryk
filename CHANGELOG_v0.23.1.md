# ElektorykScan 0.23.1 — Depth cache memory bound

- Bounded `WallTextureBaker` decoded D16 cache to 12 access-ordered grids instead of retaining every keyframe depth grid for the whole reconstruction.
- Clears the depth cache before and after each bake, including exceptional exits.
- Worst-case retained decoded D16 evidence in this cache is now ~4.8 MB (12 × 200,000 × 2 bytes) instead of growing with every referenced keyframe.
- Keeps locality: neighboring wall tiles still reuse recent depth grids, so the memory bound does not revert to decode-per-tile behavior.
- RGB bitmap LRU from v0.23.0 remains unchanged.
- versionCode 72 / versionName 0.23.1-depth-cache-memory-bound.
