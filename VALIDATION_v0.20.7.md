# ElektorykScan v0.20.7 — topology preservation pass

## Fixed
- RoomPerimeterSanitizer no longer assumes the complete scan is a single room.
- Valid floor-to-ceiling interior partitions are preserved instead of being replaced by the global rectangular bounds when wall count exceeds six.
- Added separate single-room and apartment-level complexity limits.
- Added duplicate suppression before topology decisions, preventing adjacent depth layers from consuming the architectural wall budget.
- Multi-room candidate walls are quality-ranked only after boundary/interior classification.

## Why this matters
The previous safety fallback could turn a correctly scanned apartment with >6 structural segments into one large rectangle before MultiRoomSegmenter ran. Doors, room adjacency and room areas could then become wrong even with good ARCore depth data. v0.20.7 preserves credible partitions while retaining the conservative furniture/noise fallback for scans with no partition evidence.

## Regression validation
A synthetic 8 m × 6 m apartment with four facade walls, multiple interior partition segments and duplicate boundary fragments was passed through the sanitizer. The test verifies that an interior partition survives and that the result is not collapsed to four envelope walls. Result: PASS.
