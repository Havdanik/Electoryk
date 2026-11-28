# ElektorykScan v0.26.2 — Zero-churn surface bounds

Deep scanner pass focused on long-scan stability after the RANSAC available-set fix.

## Changed
- Removed four per-plane `float[]` allocations from `DepthSurfaceTracker.integrateFit()`.
- Added grow-only reusable U/V and sorted-quantile workspaces.
- Preserved U/V pairing for tile projection; only scratch copies are sorted.
- Uses ranged `Arrays.sort(..., 0, m)`, so stale capacity outside the active sample count cannot affect robust 5.5/94.5% bounds.
- No scene schema or project migration changes.

## Why
A frame can fit up to four structural planes. The old robust-bounds stage allocated U, V, sorted-U and sorted-V arrays for every fit, adding GC pressure on top of ARCore/Depth during long scans. This pass makes that stage allocation-free after workspace warm-up without changing thresholds or geometry decisions.
