# ElektorykScan v0.24.4 — Shape-aware stairs

- Scanner-first pass: semantic STAIRS are no longer rendered as one generic cuboid in the 3D Digital Twin.
- Added a procedural stair-flight primitive driven by the scanned width/depth/height/yaw.
- Step count is estimated from measured rise (~18 cm nominal), bounded to 2..18 to stay robust against noisy depth.
- Individual treads preserve the oriented footprint and progressively rise along the detected run direction.
- Selection still uses the original semantic object envelope, so editor hit-testing/dragging remains one object instead of many synthetic steps.
- Existing scene schema remains compatible; no migration is required.
- Important limitation: current ADE20K semantic model still cannot reliably name boiler/ladder classes. Those require an additional instance/object detector rather than unsafe label heuristics.
