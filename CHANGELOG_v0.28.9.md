# ElektorykScan v0.28.9 — Robust Oriented Extents

- Fixed a semantic-geometry inconsistency left after v0.28.8: PCA/centroid used robust 3D survivors, but oriented width/depth still projected every reservoir sample.
- Oriented dimensions now use the exact paired X/Y/Z survivor set accepted by robust bounds.
- Removed redundant second projection quantile trim; the survivor set is already trimmed in 3D, avoiding systematic shrinkage of valid partial-view furniture corners.
- Preserves project/scene schema and existing semantic tracking behavior.
