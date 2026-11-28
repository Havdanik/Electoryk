# ElektorykScan v0.23.7 — Visual Evidence Path Hardening

- Treat persisted RGB/D16 keyframe filenames as untrusted input during reconstruction.
- Reject non-leaf/path-traversal names before any file access in WallTextureBaker.
- Reuse the wall-tile Matrix and destination quad scratch buffer instead of allocating them for every tile.
- No scene/project schema change; existing valid visual evidence remains compatible.
- Preserves v0.22.x–v0.23.6 epoch, atomic-write, cache-bound, decode-safety and projection optimizations.
