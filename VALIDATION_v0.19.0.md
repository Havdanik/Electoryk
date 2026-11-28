# Validation — v0.19.0 Semantic Scene AI

## Passed targeted checks

- Java 8 compile-check: model package + full semantic package + SemanticPlanFuser + SemanticFurniturePromoter + SemanticRoomClassifier — PASS (warnings only).
- Label mapping regression:
  - TensorFlow DeepLab raw 0 -> UNKNOWN/ignore
  - raw 1 -> WALL
  - raw 15 -> DOOR
  - raw 36 -> WARDROBE
  - raw 128 -> BICYCLE
  - canonical WINDOW -> raw 9
  - MLPerf raw 14 -> DOOR
  - MLPerf raw 31 -> OBJECT/OTHER
  - MLPerf WARDROBE -> unavailable (-1), never falsely mapped to another class
- Connected-component instance split is enabled before Depth fusion; disconnected same-class regions produce separate candidates.
- Semantic wall candidate rejects strongly non-planar/L-shaped point groups; structural Depth tracker remains geometry authority.
- Door/window visual masks use per-model raw label mapping before projection to reconstructed walls.
- Unknown crossed floor-level geometric opening can become PASSAGE; PASSAGE is walkable and rendered separately in 2D/3D/client views.
- Semantic AI capture cadence is independent from RGB texture keyframe cap.
- Full Gradle compile could not run in this environment because Gradle 8.2 distribution is not locally cached and services.gradle.org is unreachable here.

## Device checks required

1. First launch: verify AI model reaches READY (`AI150` or fallback `AI32`).
2. Point at a plain wall: center label should become `стіна`; active structural surface dimensions should stabilize while moving laterally.
3. Point at floor/ceiling: semantic focus should switch without creating furniture or extra walls.
4. Show a door and window from roughly frontal views; finish scan and verify each opening is attached to the correct wall.
5. Walk through an open doorway without a door leaf; verify PASSAGE rather than DOOR when geometric evidence supports it.
6. Scan two separate cabinets/wardrobes: verify two objects rather than one giant bounding box.
7. Return to a previously seen object/wall: same semantic entity/surface is refined, not duplicated.
8. Verify furniture in front of a wall does not become the wall texture or architectural wall.
