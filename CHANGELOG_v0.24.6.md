# ElektorykScan 0.24.6 — Instance Separation

Scanner-first pass focused on preventing nearby real objects from collapsing into one 3D entity.

- Added same-frame instance separation in SemanticSceneTracker. Disconnected semantic components supported by the same RGB frame are treated as distinct physical instances unless their metric Depth footprints genuinely overlap/touch.
- Added horizontal Depth-footprint gap and IoU tests before multi-view association. This specifically protects pairs such as two chairs, nightstands, cabinets, stools, radiators or neighbouring appliances.
- Added the same guard to stable-duplicate cleanup so post-frame deduplication cannot immediately undo instance separation.
- Preserved cross-view association from 0.24.5: partial views of a large sofa/bed/table can still fuse when their 3D volumes are geometrically continuous.
- No project/schema migration required; existing scene.json files remain compatible.
