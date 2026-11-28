# ElektorykScan v0.25.0 — Partial-view dimension fusion

Scanner-first reconstruction pass.

- Fixed persistent object dimensions shrinking during long multi-view scans when only part of a sofa/bed/table/cabinet is visible in many frames.
- Replaced symmetric oriented-dimension EMA with asymmetric evidence fusion: newly revealed extent is learned faster; shrinkage of mature objects requires sustained evidence.
- Added per-observation dimension jump bounds so one noisy view cannot abruptly resize an object.
- Preserved recoverability: dimensions are not a permanent max; repeated smaller observations can still correct an early overestimate.
- Existing semantic IDs, robust fused centroid, same-frame instance separation, opening projection, stairs and JSON compatibility remain unchanged.
