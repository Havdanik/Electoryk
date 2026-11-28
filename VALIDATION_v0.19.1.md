# Validation — v0.19.1 Stable Semantic Scan

## Targeted Java compile checks

PASS: `DepthSurfaceTracker`, `ArSurfaceGridRenderer`, `SemanticSceneTracker`, `SemanticFrame`, `SemanticLabel` with minimal AR/OpenGL stubs.
PASS: `IndoorSemanticSystem` with minimal Android/model stubs.

## Regression checks

- PASS: six noisy passes of one physical wall -> 1 stable wall track.
- PASS: one extreme coplanar Depth outlier does not stretch the wall beyond the expected room-scale extent.
- PASS: scan-local camera pose shock is rejected and does not create a new track.
- PASS: second real parallel wall remains a separate stable wall.
- PASS: five `WALL` center frames + one `DOOR` flicker keeps `WALL`.
- PASS: repeated strong `DOOR` frames eventually switch stable center label to `DOOR`.

Full Android/ARCore runtime validation still requires device build/run in AIDE/Android SDK environment.
