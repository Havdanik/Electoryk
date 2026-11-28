# ElektorykScan v0.23.9 — Durable export commit

- versionCode 80 / versionName `0.23.9-durable-export-commit`.
- FULL/LIGHT `.elektoryk` ZIP export now calls `ZipOutputStream.finish()` and flushes the complete ZIP central directory before publication.
- The staged package is `fsync()`ed through its `FileDescriptor` before it can become visible as the final export.
- Export publication now uses the shared rollback-aware `AtomicFileStore.replaceBinary()` path instead of direct delete/rename/copy logic.
- Incomplete/empty staging packages are rejected before commit.
- No project schema, scene geometry, scan model, texture format, or import compatibility changes.
