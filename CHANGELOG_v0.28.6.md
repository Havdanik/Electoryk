# ElektorykScan v0.28.6 — Confidence-aware object occupancy

- versionCode 127 / versionName `0.28.6-confidence-aware-object-occupancy`.
- Interior object voxel clustering now consumes fused `CloudPoint.confidence` instead of treating every confirmed point as equal evidence.
- A voxel can participate in BFS object topology only with >=2 observations and >=0.55 accumulated confidence. This closes the remaining case where two weak depth-haze points could form a bridge between separate furniture objects.
- The rule is symmetric for component seeds and neighbours, preserving the insertion-order independence introduced in v0.28.5.
- No scene/project schema change; import/export compatibility is retained.
