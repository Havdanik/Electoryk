# ElektorykScan v0.23.2 — Digital Twin texture safety

- versionCode 73 / versionName `0.23.2-digital-twin-texture-safety`.
- Hardened DigitalTwinView against oversized/corrupt imported wall textures: bounds-first decode, 32 MP source ceiling, <=2048 px sampled decode, OOM/runtime decode containment.
- A single decoded texture can no longer exceed/defeat the 32 MiB wall-texture cache budget.
- Texture cache identity now includes filename + length + mtime, preventing stale wall imagery after an in-place texture re-bake.
- Generated textures (max 1400x900) keep full resolution; no project/schema format changes.
