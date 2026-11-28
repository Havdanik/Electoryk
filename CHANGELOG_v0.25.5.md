# ElektorykScan v0.25.5 — Final-axis duplicate fusion

Scanner-first geometry integrity pass.

- Fixed a coordinate-frame inconsistency in semantic duplicate consolidation.
- The final axial yaw is now chosen before footprint dimensions are fused.
- Both persistent and duplicate oriented rectangles are projected into that same final local frame.
- Prevents repeated duplicate merges from leaving width/depth in a stale axis, slowly inflating furniture footprints or making dimensions disagree with yaw.
- Added small axial-angle/projection helpers with no Android dependencies (AIDE-safe Java).
- Scene JSON schema is unchanged; existing projects remain compatible.
