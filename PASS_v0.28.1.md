# ElektorykScan v0.28.1 — Balanced Architectural Plane Retention

## Deep-pass change
PlaneEvidenceTracker no longer treats the 28-plane working set as a single undifferentiated area-ranked pool. v0.27.7 fixed ARCore iteration-order dependence, but a dense family of fragmented walls could still evict every horizontal candidate, while many horizontal surfaces could reduce wall evidence.

v0.28.1 records the ARCore plane family (vertical / upward-facing horizontal / downward-facing horizontal) and applies bounded, category-aware retention. The working set now protects at least one upward and one downward horizontal candidate when observed, maintains a useful reserve of vertical candidates for wall/yaw guidance, prefers same-family replacement, and requires a 12% score margin for cross-family eviction to reduce selection churn.

The protected horizontal candidates are NOT automatically accepted as floor/ceiling. Existing metric gates remain in force: transformed normal, camera-relative height, minimum area, relative-area gate, height stabilization and multi-update locking. Tables/shelves therefore remain excluded from architectural floor/ceiling classification unless they pass the existing structural tests.

No scene.json schema change. Existing projects remain compatible.
