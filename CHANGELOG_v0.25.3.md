# ElektorykScan v0.25.3 — Mature Instance Identity Guard

- Fixed a late-stage multi-view identity regression in `SemanticSceneTracker.mergeStableDuplicates()`.
- Two established neighbouring objects of the same/compatible class are no longer merged merely because their centres and noisy AABBs fall inside the duplicate-cleanup gate.
- Mature-track duplicate cleanup now requires stronger metric evidence: horizontal footprint overlap, vertical overlap, and a centre-separation sanity check relative to learned oriented extents.
- Door/window tracks keep the more permissive path because partial observations of one opening can legitimately have little footprint overlap.
- Same-frame separation, partial-view association, robust fused centre, orientation stability and asymmetric dimension fusion remain intact.
- No scene JSON schema change.
