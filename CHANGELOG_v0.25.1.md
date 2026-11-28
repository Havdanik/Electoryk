# ElektorykScan v0.25.1 — Orientation / axis stability

- Added PCA orientation reliability from horizontal covariance anisotropy.
- Near-square/noisy/partial point clouds no longer steer persistent object yaw.
- Per-view width/depth are now measured in the persistent object's frame after yaw fusion, preventing 90° width/depth swaps.
- Orientation update strength scales with geometric reliability.
- Keeps v0.25.0 asymmetric partial-view dimension fusion and all earlier semantic/Depth safeguards.
