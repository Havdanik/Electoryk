# ElektorykScan v0.20.9 — footprint-aware coverage/gaps

Deep-pass focus: irregular apartment geometry after v0.20.8.

## Fixed
- ScanGapDetector now accepts ApartmentPlan and restricts missing-zone detection to actual RoomZone floorRuns.
- L/U-shaped empty bounding-box quadrants are no longer reported as giant missing scan zones.
- Gap detection is intentionally executed after MultiRoomReconstructionEngine so it can use topology.
- floorObservationRatio is recomputed against reconstructed room area when topology is available instead of rectangular RoomBounds.
- MultiRoomReconstructionEngine confidence coverage uses direct observed floor / reconstructed room footprint, avoiding confidence penalties for valid concave plans.
- Legacy ScanGapDetector overloads remain source-compatible and preserve old behavior when no ApartmentPlan exists.

## Validation
- 220 Java sources checked for balanced structure.
- No dependency/API level changes.
- Gradle build attempted; bootstrap is blocked by unavailable services.gradle.org / missing cached Gradle 8.2 in this environment.
