# Validation — ElektorykScan v0.22.0

- Source tree: 221 Java files.
- Lexical delimiter scan after stripping comments/strings: 0 mismatches.
- AndroidManifest: 41 declared components, including 39 activities; 0 missing corresponding project Java classes.
- Java 8 stub compile: current `SceneRepository` PASS.
- Java 8 stub compile: current `ProjectPackageManager` PASS.
- Java 8 compile: `RoomAnalyzer`, `RoomAxisEstimator`, `RoomFootprint` PASS.
- Scene transaction harness: `SCENE_PAIR_TX_AND_IMPORT_RECOVERY_PASS`. Tested PREPARING discard, PREPARED roll-forward, FULL scene without point cloud, authoritative FULL without scene, clear-second/clear-all recovery states, and checkpoint `replacePrevious`.
- Standalone synthetic reconstruction benchmark (~370k points, same generated cloud): v0.21.6 ~181064 KB peak RSS / 2.77 s; v0.22.0 ~95864 KB / 1.15 s; detected yaw remained 22.32°. This is a JVM regression benchmark, not an Android-device benchmark.
- Full Android `./gradlew assembleDebug` was attempted. Build bootstrap cannot resolve `services.gradle.org` and no Gradle 8.2 distribution is cached in this execution environment, so APK compilation cannot be honestly certified here. Failure occurs before Android/Java compilation.
- Final archive must pass `unzip -t` after packaging.
