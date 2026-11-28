# v0.17.2 validation

- Modified scanner-core Java compiled with Android/ARCore API stubs: PASS.
- Depth timestamp regression: PASS (`raw=2 smooth=1 dup=1 vox=410`).
- 200 Java source files present.
- 39 source Activities / 39 Manifest Activities.
- AndroidManifest XML parses.
- 8 resource XML files parse, 0 failures.
- No remaining `rawTs != frameTs` or `depthTs != frameTs` rejection path in `ArCoreDepthCollector`.
- Project retains `jniLibs.useLegacyPackaging true`.
- Project root is AIDE-ready (`app/`, `gradlew`, `settings.gradle` directly at ZIP root).
