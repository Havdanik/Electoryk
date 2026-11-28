# ElektorykScan 0.23.0 — Texture Bake LRU Hardening

- Fixed a heavy post-scan performance hotspot in `WallTextureBaker`: the same RGB keyframe could be decoded from JPEG once per 0.52 m wall tile, causing repeated native bitmap allocations, JPEG decode CPU and GC pressure during finalization.
- Added a bounded per-wall access-order cache of at most 3 decoded keyframes. Neighboring tiles normally reuse the same preferred frame, so this removes most duplicate decodes while keeping native bitmap memory bounded.
- Cache entries are explicitly recycled on eviction and in `finally`, including exceptional paths.
- Removed per-corner temporary `float[3]` allocations from frame projection and changed inverse final-coordinate conversion to write into caller-provided arrays.
- No project/schema/geometry format change. Existing scans and all v0.22.x recovery/epoch/backpressure protections remain compatible.
- Version: `0.23.0-texture-bake-lru-hardening`, versionCode 71.
