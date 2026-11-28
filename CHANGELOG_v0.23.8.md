# ElektorykScan v0.23.8 — Atomic Texture Directory Swap

- versionCode 79 / versionName `0.23.8-atomic-texture-directory-swap`.
- FULL import no longer deletes the live `scene/textures` directory before the replacement generation is ready.
- The previous texture generation is renamed to `textures.importbak`, the fully staged generation is atomically published, and the backup is removed only after success.
- If publication fails, the old generation is restored instead of leaving Digital Twin with a missing/partial texture set.
- FULL packages that intentionally contain no referenced wall textures still publish a clean empty generation and remove stale old textures.
- Project/package schema is unchanged; no migration is required.
