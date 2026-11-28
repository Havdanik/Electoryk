# ElektorykScan v0.26.8 — Zero-boxing Scan Fusion

Deep pass focus: resumed/multi-pass scan alignment stability and finish-time memory pressure.

## Changes
- Replaced `ArrayList<Float>` coordinate collection in `ScanFusionEngine.stats()` with bounded primitive `float[]` buffers and range sorting. This removes tens of thousands of boxed `Float` objects per tested quarter-turn.
- Removed `double[2]` allocation from every X/Z quarter-turn transform in stats, overlap scoring, point fusion and path fusion.
- Replaced `HashSet<Long>` occupancy scoring with a compact primitive open-addressed `LongVoxelSet`, eliminating boxed `Long` churn across repeated coarse/refined bidirectional overlap evaluations.
- Preserved all existing alignment semantics: 4 quarter-turn hypotheses, robust 15/50/85% structural quantiles, floor quantile, reciprocal overlap, 12.5 cm refinement and 5% rejection threshold.
- No scene/project schema change.

## Validation
- Source structural checks across all Java files.
- ZIP integrity validation.
- Android build attempted where environment permits.
