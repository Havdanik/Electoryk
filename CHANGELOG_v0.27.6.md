# ElektorykScan v0.27.6 — Zero-allocation plane matching

- Removed per-Depth-sample `PlaneEvidenceTracker.Match` allocation from live ARCore ingestion by adding `matchInto(...)` and a collector-owned reusable result object.
- Preserved plane support scoring, polygon gates, wall/floor/ceiling bands, rescue thresholds and coverage semantics.
- Removed the per-collected-frame `DepthProjectionMath.Intrinsics` allocation from the live collector; scaled texture intrinsics are now computed as primitive scalars and passed to an allocation-free unprojection overload.
- Kept the allocating compatibility APIs for tests/cold callers, so existing call sites and behavior remain source compatible.
- No project/scene schema migration.
