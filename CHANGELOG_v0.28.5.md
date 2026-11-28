# ElektorykScan v0.28.5 — Object Cluster Speckle Guard

- Fixed an order-dependent semantic reconstruction bug in `InteriorObjectDetector`.
- One-hit depth voxels are now rejected consistently both as component seeds and during BFS traversal.
- Prevents sparse depth speckles from acting as bridges that merge two independent furniture/object clusters.
- Preserves the existing primitive voxel map, six-neighbour topology, room-boundary exclusion and review classification.
- No scene/package schema change; existing projects remain compatible.
