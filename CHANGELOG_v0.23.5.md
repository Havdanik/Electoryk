# ElektorykScan v0.23.5 — Depth Grid Decode Safety

- Hardened D16 wall-texture evidence decoding against integer-overflow dimensions.
- D16 cell count is now computed in `long` and strictly bounded to 200,000 cells before allocation.
- Rejects truncated D16 payloads before allocating a depth grid.
- Handles OOM from hostile/corrupt depth evidence without aborting the reconstruction pass.
- Recycles an unfinished wall output bitmap immediately when bakeWall aborts with OOM/runtime failure, reducing memory-pressure cascades.
- Preserves all v0.23.4 atomic PNG commit and prior scan/recovery hardening.
