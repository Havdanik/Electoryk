# ElektorykScan v0.26.3 — Semantic Frame Normalization Hardening

- Fixed semantic entity coordinate normalization after room-axis alignment: robust fused centroids now rotate with entity bounds instead of remaining in stale scan coordinates.
- Fixed semantic oriented yaw normalization: `rotationDeg` is transformed into normalized-plan coordinates, keeping furniture orientation and door/window width projection aligned with reconstructed walls.
- Removed temporary corner arrays from semantic AABB normalization.
- Removed per-plane RANSAC inlier-index allocation in `DepthSurfaceTracker`; a grow-only reusable index workspace is used and all consumers honor the active `Fit.count` range.
- Preserves scene JSON schema and existing projects.
