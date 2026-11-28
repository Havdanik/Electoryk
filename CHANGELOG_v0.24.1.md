# ElektorykScan v0.24.1 — Semantic Depth Object Fusion

Scanner-first pass focused on incorrect object geometry rather than export hardening.

- Semantic/Depth fusion now erodes one mask pixel at class boundaries before accepting a 3D Depth point. This rejects the common foreground-mask/background-depth leak that inflated chairs, sofas, doors and other objects into wall-sized boxes.
- Every semantic component keeps a fixed-memory deterministic 3D reservoir and derives its persisted bounds from trimmed 3.5%/96.5% quantiles instead of raw extrema. A few bad ARCore Depth samples can no longer dictate object dimensions.
- Object horizontal orientation is estimated from the fused X/Z covariance (PCA), persisted in SemanticEntity.rotationDeg and temporally smoothed across observations.
- Promoted editable Digital Twin furniture now inherits the learned rotation instead of always being axis-aligned.
- Semantic stairs and columns are now promoted as editable CUSTOM scene objects instead of being detected and then silently discarded by SemanticFurniturePromoter.
- Wall-mounted semantic items (mirror, shelf, television, radiator) are tagged placement=WALL.
- Persistent JSON remains backwards compatible: older projects simply default rotationDeg to 0.

Known boundary: ADE20K semantics cannot provide a dedicated boiler class. Unknown classes still become generic OBJECT geometry where the selected model exposes them. A dedicated indoor object detector / instance segmentation model remains the next scanner milestone for boiler/ladder and fine-grained appliances.
