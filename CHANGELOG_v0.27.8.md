# ElektorykScan v0.27.8 — Opening Traversability Hardening

- Hardened continuous-wall door inference against false doors caused by a slightly misplaced wall line crossing the camera path.
- A path crossing now needs direct (non-inferred) floor evidence on both sides for an interior door.
- Exterior door candidates may retain one-sided floor evidence when the wall is independently classified as an exterior floor edge.
- Uses `rawFloor`, not hole-filled/inferred floor, so furniture/occlusion filling cannot manufacture door traversability evidence.
- Door confidence now reflects two-sided vs exterior one-sided support.
- Existing window profile logic, structural shoulders, dimensions, semantic fusion and project format remain unchanged.
- versionCode 119 / versionName `0.27.8-opening-traversability-hardening`.
