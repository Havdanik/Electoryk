# ElektorykScan v0.29.0 — Semantic History Ring

Deep-pass focus: long-scan semantic memory stability and hot-path housekeeping.

## Fixed
- Replaced the semantic history `ArrayList` eviction path with `ArrayDeque`.
- The history is bounded by both 96 records and 3,000,000 retained mask pixels, but the old implementation used `remove(0)` for every eviction. That shifts every surviving reference on each semantic inference once the history is full.
- Long scans now evict the oldest semantic record in O(1) with `pollFirst()`, avoiding periodic reference-array copies on the semantic worker and reducing avoidable CPU/GC pressure while ARCore/Depth are active.
- Preserved chronological snapshot/export behavior: `new ArrayList<>(records)` keeps deque iteration order oldest -> newest.
- Added a defensive null/break guard around eviction so accounting cannot spin if future history mutations change independently.

## Compatibility
- No scene.json/schema change.
- No model or project migration.
- Existing scanning, plane/depth fusion, semantic entities, 2D/3D editor, electrical/furniture library and export paths remain compatible.
