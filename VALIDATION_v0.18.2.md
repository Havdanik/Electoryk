# Validation — v0.18.2 Clean Surface Scanner

## Passed locally

- `DepthSurfaceTracker` pure-Java compile-check.
- Regression: six noisy observations of one physical wall -> exactly 1 stable wall track.
- Regression: add a second parallel physical wall 3 m away -> exactly 2 stable wall tracks.
- Regression: room wall + large wardrobe side + floor + ceiling -> wardrobe stays non-architectural; wall remains architectural.
- `ArSurfaceGridRenderer` Java compile-check with minimal Android/OpenGL/ARCore stubs after active-surface / recent-vs-memory rendering changes.
- `ScanVisualKeyframeRecorder` + `WallTextureBaker` Java compile-check with minimal Android/ARCore stubs after RGB+coarse-depth keyframes and occlusion-aware texture selection.
- `ApartmentPlanView` Java compile-check with minimal Android stubs after real-wall rendering and clean single-room fill.

## Full Android build limitation

This environment has no cached Gradle 8.2 / Android SDK / ARCore dependencies and cannot resolve `services.gradle.org`, so a truthful installable APK is not produced here. Build the source ZIP in AIDE/Android with dependencies available.

## Field test expected behavior

1. Start **Новий Smart Scan з нуля**.
2. Camera overlay should show only the currently observed wall/floor/ceiling, not the whole saved room through furniture.
3. Revisit a wall: its stored coverage returns faintly; newly confirmed cells brighten.
4. A tall cabinet side should not increase the stable wall count unless it truly behaves like room-scale architecture.
5. After finish, a simple room should show real plan walls in 2D; the trajectory is faint.
6. RAW 3D texture should not copy an occluding wardrobe/curtain onto the wall when synchronized depth evidence says it was in front of the wall.
