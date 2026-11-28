# Validation — v0.18.0 Structural Room Model

## Passed locally
- Java brace/syntax sanity across all modified source files.
- `OpeningDetector` compiles with minimal JVM stubs after stricter opening logic.
- `DepthSurfaceTracker` synthetic revisit regression: one physical wall observed from four positions remains exactly one track.
- `DepthSurfaceTracker` synthetic room regression: four physical room walls produce four stable wall tracks and satisfy structural readiness.
- `StructuralWallExtractor` synthetic furniture regression: two architectural walls + dense furniture + a tall 0.9 m wardrobe side produce exactly two walls.
- Edge-wall regression: walls at room min/max histogram bounds are retained.
- Previously checked with local stubs: `ArSurfaceGridRenderer`, RGB keyframe recorder/texture baker, and `DigitalTwinView` compile paths.

## Full Android build limitation in this environment
The project uses Gradle 8.2 / Android Gradle Plugin and ARCore. This container has no Android SDK/Gradle distribution cache and cannot resolve `services.gradle.org`, so a truthful installable APK cannot be produced here. Build the ZIP in AIDE/Android environment with dependencies available.

## Known limitation
True indoor semantic recognition (door/window/mirror/furniture class) is not bundled in v0.18.0. Geometry-only heuristics remain conservative and uncertain openings are kept UNKNOWN rather than fabricated.
