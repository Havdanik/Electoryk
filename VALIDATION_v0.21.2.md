# ElektorykScan v0.21.2 validation

Focus: irregular-footprint structural wall extraction.

- Source structural brace scan: PASS (221 Java files).
- Synthetic L-footprint floor-side test: PASS.
  - concave horizontal facade at z=3 m: recognized as boundary;
  - ordinary interior plane with floor on both sides: rejected as footprint boundary;
  - concave vertical facade at x=3 m: recognized as boundary.
- No new dependencies and no persistence schema changes.
- Full Android assembleDebug remains environment-dependent; validate in AIDE/Gradle before release installation.
