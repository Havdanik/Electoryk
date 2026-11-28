# ElektorykScan 0.24.2 — Oriented Object Geometry

- Semantic Depth objects now keep robust dimensions in their own PCA-aligned horizontal frame instead of using only world-axis AABBs.
- Fixes inflated width/depth for sofas, beds, tables, cabinets and other objects scanned at an angle to the room axes.
- Oriented dimensions use the same trimmed/reservoir Depth evidence as the robust AABB, so silhouette/background outliers remain suppressed.
- Multi-view observations smooth oriented width/depth and 180-degree-symmetric yaw instead of replacing geometry from one frame.
- Duplicate entity fusion now combines yaw and oriented dimensions weighted by observation count.
- `SemanticEntity` persists `orientedWidthM` / `orientedDepthM`; legacy scenes with absent/zero fields automatically fall back to the previous AABB dimensions.
- Room-envelope surfaces keep the existing structural geometry path; oriented object dimensions are not allowed to redefine walls/floor/ceiling.
- versionCode 83 / versionName `0.24.2-oriented-object-geometry`.
