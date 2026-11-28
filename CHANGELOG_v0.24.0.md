# ElektorykScan v0.24.0 — Export Snapshot Consistency

- versionCode 81 / versionName `0.24.0-export-snapshot-consistency`.
- FULL export now hashes every binary again while those exact bytes are streamed into the ZIP.
- The streaming digest must match the checksum generation already written to `manifest.json`; if a point cloud, wall texture, evidence file, signature, or consultation audio changes during a long export, packaging aborts instead of publishing a self-inconsistent `.elektoryk` file.
- Keeps v0.23.9 durable staging (`finish` + flush + fsync + rollback-aware atomic publication).
- No project schema or import compatibility change.
