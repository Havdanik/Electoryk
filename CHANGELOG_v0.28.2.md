# ElektorykScan v0.28.2 — Lazy Plane Polygon Materialization

- ARCore plane refresh no longer copies every detected plane polygon before architectural selection.
- Polygon area is measured directly from a duplicated FloatBuffer without allocating a float[].
- Exact polygon coordinates are materialized only when the candidate survives the bounded 28-plane architectural working set.
- retainArchitectural() now reports whether a candidate was actually admitted/replaced, preserving v0.28.1 family balancing and 12% cross-family hysteresis.
- Point-to-polygon structural gating remains unchanged for retained planes.
- Project format / scene.json unchanged.
- versionCode 123 / versionName 0.28.2-lazy-plane-polygon-materialization.
