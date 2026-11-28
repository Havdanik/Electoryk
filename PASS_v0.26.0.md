# ElektorykScan v0.26.0 — Zero-churn Depth Projection

Scanner-first stability/performance pass on top of v0.25.9.

## Changes
- Removed per-sample float-array allocation from the live ARCore Depth projection loop.
- Added `DepthProjectionMath.unprojectInto(...)` allocation-free API while preserving the old allocating API for compatibility.
- Reused camera/world/scan-local 3-float transform buffers with ARCore Pose in-place overloads.
- Added reusable grow-only integer sampling workspaces for raw and smoothed depth frames.
- Added reusable grow-only float projection workspaces for accepted depth points.
- Moved raw-depth local-support neighbor offsets to static constants; previously two arrays were allocated for every weak-depth candidate tested.
- Reused forward-axis vectors for camera yaw estimation.
- Kept immutable published `DepthEvidenceFrame` arrays isolated: reusable workspaces are copied before publication, preventing later frames from mutating reader-visible evidence.
- No scene.json/schema changes and no intentional reconstruction/threshold changes.

## Validation
- 221 Java source files scanned for structural brace balance: 0 failures.
- `DepthProjectionMath.java` compiles standalone with javac.
- Full Android assembleDebug attempted; environment cannot resolve services.gradle.org to obtain Gradle 8.2, so final Android/AIDE compile acceptance remains external.
