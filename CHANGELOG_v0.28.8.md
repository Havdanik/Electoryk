# ElektorykScan v0.28.8 — Robust Semantic Pose

- Semantic 3D candidate AABB already used trimmed quantiles, but centroid, PCA yaw and wall-planarity still used untrimmed raw moments. Boundary Depth leaks could therefore rotate or drag furniture while its visible box looked correct.
- Rebuild paired X/Y/Z moments from the same reservoir samples that survive robust X/Y/Z quantile bounds.
- Semantic centroid, orientation reliability, PCA yaw and horizontal planarity now share the robust evidence set.
- Keep raw support count for confidence/observation strength and fall back to raw moments only for tiny components where trimming cannot be stable.
- No scene.json schema change; existing projects remain compatible.
- versionCode 129 / versionName 0.28.8-robust-semantic-pose.
