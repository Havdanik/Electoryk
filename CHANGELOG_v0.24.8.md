# ElektorykScan 0.24.8 — Robust Fused Object Center

- Added a persistent robust fused centroid to every semantic 3D entity.
- Multi-view association and scene promotion now use a recoverable EMA centroid instead of the center of a monotonically expanding union AABB.
- Large centroid jumps are down-weighted so one partial/bad Depth view cannot permanently pull an object identity away from the physical object.
- Duplicate-track merges combine fused centroids by observation support.
- JSON persistence is backward compatible: old scenes fall back to their legacy AABB center until a new observation establishes a fused center.
- Existing semantic instance IDs, oriented dimensions, opening projection, stairs reconstruction and export format remain intact.
- versionCode 89 / versionName 0.24.8-robust-fused-object-center.
