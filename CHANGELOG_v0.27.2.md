# ElektorykScan v0.27.2 — Primitive Final Fusion

- Removed the remaining `LinkedHashMap<Long, CloudPoint>` from final multi-pass cloud fusion. The 4.5 cm voxel merge now uses a primitive open-addressed `long -> CloudPoint` table, retaining the highest-confidence point per voxel without `Long` boxing or linked hash nodes.
- The primitive table pre-sizes from the two input clouds, grows at 70% load, and appends only occupied values to the fused result. Fusion geometry, voxel size, overlap acceptance, quarter-turn alignment and confidence winner semantics are unchanged.
- Removed two per-event float-array allocations from `MotionTracker`; rotation matrix and orientation buffers are now reused for the lifetime of the tracker.
- Project format and `scene.json` are unchanged; no migration is required.
