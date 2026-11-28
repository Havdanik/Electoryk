# Validation — ElektorykScan v0.22.1

- Base: v0.22.0 FINAL RELEASE HARDENING.
- Focus: long-scan semantic inference memory pressure and lifecycle shutdown race.
- TFLite input/output direct buffers are now reused and capacity-checked instead of allocated every inference.
- `IndoorSemanticSystem.submit()` resets `busy` when executor submission races with shutdown.
- Version: 0.22.1-long-scan-memory-hardening, versionCode 62.
- Source delimiter scan and archive integrity are part of packaging validation.
- Full Android/AIDE APK compile is not claimed in this container unless the Android Gradle toolchain is locally available.

## Run results
- Java source files: 221.
- Lexical delimiter scan: PASS, 0 mismatches.
- Gradle assembleDebug attempted; bootstrap cannot resolve services.gradle.org and no Gradle 8.2 distribution is cached, so Android/AIDE compile remains device-side acceptance. Failure occurred before project compilation.
- ZIP is packaged with project files at archive root (no extra project-in-project directory).
