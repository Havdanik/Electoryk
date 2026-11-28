# ElektorykScan 0.21.4 — Editor Spatial Integrity

- Furniture drag/nudge is now footprint-aware. Objects cannot silently end up in the void of an L/U-shaped room; crossing into a real adjacent room updates `roomId`, while invalid drops snap to the nearest valid floor cell.
- Manual door/window edits now recompute wall binding, orientation, roomA/roomB and external/internal topology from both sides of the actual wall.
- Windows are kept external and cannot accidentally become room-to-room passages after a manual resize/type edit.
- Opening wall rebinding is distance-gated to avoid attaching an orphan opening to an unrelated distant wall.
- Project diagnostics now detect orphan opening room IDs, missing opening wall IDs, orphan furniture room IDs, non-finite furniture/opening geometry, and furniture outside its owning room footprint.
- No storage schema or dependency changes; Java 8/AIDE project structure retained.
