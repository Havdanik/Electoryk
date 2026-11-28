# ElektorykScan v0.26.7 — Zero-churn live overlay

- Hardened the AR structural coverage renderer, which executes on every camera frame.
- Reuses active/architectural surface lists instead of allocating ArrayLists every frame.
- Pools mutable scored-surface records used for active-surface ranking.
- Reuses local/world/camera/normal vectors for surface visibility and incidence tests.
- Reuses draw-corner scratch vectors across all wall/floor/ceiling tile passes.
- Preserves active-surface hysteresis, semantic focus, topology readiness and tile rendering behavior.
- No scene.json schema change; no project migration required.
