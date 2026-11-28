# ElektorykScan v0.21.1 — topology preview consistency

Deep-pass focus: eliminate the remaining rectangular-envelope lie in the legacy RAW/CLEAN 3D scene preview.

## Fixed
- `ScenePreviewActivity` no longer renders `RoomBounds` as a fake rectangular room when a reconstructed `ApartmentPlan` exists.
- It now renders every actual `PlanWallSegment` from floor to ceiling, so L/U-shaped rooms and multi-room apartments keep their true topology in the diagnostic 3D preview.
- `RoomBounds` box remains only as a compatibility fallback when topology is unavailable.
- Final scan summary now labels width × length as `envelope` and distinguishes it from real observed floor area, avoiding misleading dimensions for irregular footprints.

## Compatibility
- No new dependency.
- Java 8 syntax.
- Existing JSON/storage schema unchanged.
- Existing RAW point cloud and review-issue rendering preserved.
