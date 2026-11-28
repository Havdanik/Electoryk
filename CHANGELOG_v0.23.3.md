# ElektorykScan v0.23.3 — Texture Baker Decode Safety

- versionCode 74 / versionName `0.23.3-texture-baker-decode-safety`.
- Hardened `WallTextureBaker` against corrupt, replaced or unexpectedly huge RGB keyframes.
- Keyframe JPEGs are now bounds-probed before Bitmap allocation and rejected above 8 MP.
- Decode-time `OutOfMemoryError` / malformed-image failures degrade to missing photo evidence instead of crashing Digital Twin reconstruction.
- Normal recorder keyframes are unchanged; geometry, depth consistency, wall UV mapping and project format are unchanged.
- Preserves all v0.22.x–v0.23.2 recovery, epoch, cache and import/viewer hardening.
