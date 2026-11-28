# ElektorykScan v0.22.3 — Keyframe Atomic Commit

- RGB JPEG keyframes are now written through `.part` + fsync + rename instead of directly into the final file.
- Coarse D16 depth companions use the same crash-consistent commit path.
- Failed/ENOSPC keyframe writes now remove their metadata record and any incomplete final files, preventing ghost texture frames from poisoning later Digital Twin texture baking.
- Preserves v0.22.2 bounded keyframe backpressure (max 3 pending writes) and v0.22.1 reusable TFLite buffers.
- versionCode 64 / versionName 0.22.3-keyframe-atomic-commit.
