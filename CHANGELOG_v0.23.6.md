# ElektorykScan v0.23.6 — Texture projection hot-path

- versionCode 77 / versionName `0.23.6-texture-projection-hotpath`.
- Refactored `WallTextureBaker.bestFrame()` so final-to-raw tile transforms, tile normal and tile center are computed once per wall tile instead of once per candidate keyframe.
- Reuses projection scratch buffers (`quad`, corner depths and ARCore point buffers) across up to 80 candidate keyframes; only the current winning quad is copied.
- Removes repeated `double[4][3]`, `float[8]`, `double[4]` and point-buffer allocations from the texture-scoring inner loop without changing scoring, Depth consistency, geometry or project format.
- Preserves v0.23.5 D16 bounds/overflow hardening and all earlier atomic/recovery/cache protections.
