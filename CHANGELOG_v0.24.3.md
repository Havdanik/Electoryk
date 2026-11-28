# ElektorykScan 0.24.3 — Opening Wall Projection

- Door/window semantic 3D extents are now projected into the matched wall tangent instead of using max(world/oriented width, depth).
- Residual depth behind an opening can no longer directly inflate its wall opening width.
- Opening width is clamped to the physically available wall span, preventing geometry from crossing wall endpoints.
- Semantic doors are anchored to the reconstructed floor and receive conservative architectural height limits, eliminating floating doors caused by sparse lower-edge depth.
- Window vertical bounds are clamped to reconstructed floor/ceiling and repaired when sparse depth collapses their height.
- Existing semantic multi-view fusion, oriented object geometry, import/export and editor behavior are preserved.
