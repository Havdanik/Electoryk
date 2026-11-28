# ElektorykScan v0.25.6 — Scanner GC & Memory Hardening

- Replaced boxed `ArrayDeque<Integer>` connected-component flood fill with a bounded primitive `int[]` queue. This removes tens of thousands of temporary Integer allocations from a semantic-frame hot path and reduces GC pressure during ARCore + Depth scanning.
- Replaced the old semantic-history count-only retention (`240` full masks) with a dual cap: at most 96 records and at most 3,000,000 retained mask pixels. This prevents high-resolution semantic masks from silently retaining tens of MiB during long scans while preserving recent diagnostic history.
- Added exact retained-pixel accounting and reset cleanup. No scene JSON/schema changes.
- Preserved v0.25.5 stable instance identity, robust center, axis-aware duplicate fusion and partial-view geometry behavior.
