# ElektorykScan v0.25.7 — Zero-churn component workspace

- Reuses the semantic connected-component `int[]` ID and flood-fill queue buffers across inference frames.
- Removes two mask-sized primitive allocations per semantic integration that remained after v0.25.6 removed `Integer` boxing.
- At a 256×256 mask this avoids ~512 KiB of transient array allocation per semantic frame; at 512×512 it avoids ~2 MiB/frame.
- Workspace reuse is thread-safe because `integrate()` is synchronized; buffers only grow to the largest observed mask and never alter scene/project serialization.
- Existing semantic identity, robust center, orientation, partial-view fusion, duplicate guards and record-memory cap are unchanged.
