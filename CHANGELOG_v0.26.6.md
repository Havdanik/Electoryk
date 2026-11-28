# ElektorykScan v0.26.6 — Primitive Room Segmentation Hardening

- Reworked `MultiRoomSegmenter` flood-fill to a single primitive `int[]` queue; removed per-cell `new int[]{r,c}` allocations and `ArrayDeque<int[]>` churn.
- Removed the per-room full-size `boolean[rows][cols]` membership bitmap. The existing component-label grid is now the authoritative membership map for floor runs and perimeter calculation.
- Preserved room area, centroid, bounds, floor runs, compactness/confidence, small-component rejection, door sealing and opening-to-room assignment semantics.
- The queue is allocated once per segmentation pass and reused for every room component, bounding temporary memory to one integer per grid cell.
- No scene/project serialization format change; no migration required.
