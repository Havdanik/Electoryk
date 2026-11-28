# ElektorykScan v0.23.4 — Atomic Texture Commit

- WallTextureBaker no longer writes final wall PNGs in-place.
- Each texture is encoded to a `.part` staging file, `flush()`ed and `fsync()`ed, validated non-empty, then committed through `AtomicFileStore.replaceBinary()` with backup/rollback semantics.
- Scene wall texture metadata is published only after the binary commit succeeds, preventing scene state from referencing a partial new PNG.
- `Bitmap.compress()` false is now treated as a failed encode instead of silently accepting it.
- A transient `OutOfMemoryError` while allocating a wall bake bitmap skips that wall instead of aborting the entire reconstruction pass.
- Existing RGB/D16 LRU bounds, decode guards, epoch isolation, keyframe durability and Digital Twin texture safety remain intact.
