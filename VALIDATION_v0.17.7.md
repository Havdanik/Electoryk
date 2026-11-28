# Validation — v0.17.7 Metric Grid 10 cm

- `ArSurfaceGridRenderer.TILE_M = 0.10f` (10 cm in ARCore world/plane coordinates).
- Cell inset = 0.003 m per edge; visible square is 9.4 cm across with a 6 mm gap between neighboring filled regions.
- Shader border band narrowed for the smaller metric cells.
- Draw capacity increased 900 -> 2200 tiles.
- Live UI explicitly shows `Сітка 10 см`.
- Existing Raw Depth unprojection intentionally kept aligned with Google's official `raw_depth_java` sample: texture intrinsics are scaled to the current depth image dimensions and depth mm are converted to meters.
- Static brace/string sanity checks pass for changed Java files.

Field sanity target: an 80–90 cm door panel should span roughly 8–9 metric cells when the detected plane covers the panel. If the apparent grid is still grossly different, capture a screenshot with a known-size reference (door width/tape measure) because that indicates device/session projection/pose scale rather than the tile constant.
