# v0.20.4 deep-pass validation

## Audited
- ARCore/Depth lifecycle and GL-thread workload.
- Periodic/recovery point-cloud checkpoints and final fusion path.
- Semantic model + inference executors.
- RGB/depth keyframe recorder lifecycle.
- Digital Twin texture memory ownership.
- Project package import/export safety and atomic storage paths.
- Android manifest / AIDE Gradle configuration.

## Fixes applied
1. `ScanVisualKeyframeRecorder` implements `AutoCloseable`; pending IO is flushed, executor shut down, then force-stopped only if it does not terminate promptly.
2. `SemanticModelManager` implements `AutoCloseable`; its provisioning executor is released with the owning semantic system.
3. `ArCoreScanActivity.onDestroy()` closes the keyframe recorder; `onPause()` only flushes it.
4. `DigitalTwinView` scanned wall textures are capped at 32 MiB using access-order eviction and are recycled on detach/project change.

## Build verification
A full Gradle build was attempted in the automation container. The project bootstrap correctly tried to obtain Gradle 8.2, but this runtime has no DNS/network access to `services.gradle.org`, and no Gradle 8.2 distribution is cached. Therefore APK compilation cannot be truthfully claimed in this environment. Source-level structural checks were run instead; final compile should be performed in AIDE/Android environment with Gradle 8.2 available.
