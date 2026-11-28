# ElektorykScan v0.20.8 — irregular perimeter preservation

Deep pass focus: reconstruction topology for L/U-shaped rooms and apartment notches.

## Fixed
- RoomPerimeterSanitizer no longer assumes every exterior wall lies on global RoomBounds min/max.
- Added floor-side voting across 10–30 cm from each structural wall to recognize inset facade/perimeter runs.
- Credible observed irregular perimeters may retain up to 12 runs instead of being replaced by a rectangular envelope after the old >6-wall heuristic.
- Conservative rectangle rescue remains for weak/noisy wall forests and excessive parallel layers.
- This prevents invented floor area across alcoves/notches and preserves topology for downstream room segmentation, openings, 2D plan and Digital Twin.

## Regression intent
A scanned L/U-shaped footprint must retain its inset exterior runs even when those runs are far from global min/max bounds. Furniture/noise without one-sided floor evidence must not receive the relaxed perimeter budget.
