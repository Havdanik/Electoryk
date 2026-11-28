# ElektorykScan v0.21.6 — Lifecycle ANR hardening

- Removed synchronous RGB/depth keyframe flush from `ArCoreScanActivity.onPause()`.
- Added ordered `flushAsync()` so pending JPEG/depth writes complete before metadata is persisted without blocking UI.
- Added `closeAsync()` for keyframe recorder; lifecycle teardown no longer waits up to 1.5 s on the main thread.
- Semantic shutdown no longer calls `flush()` on the main thread while TFLite inference may still be running.
- Semantic cleanup is queued after in-flight inference, then executor is shut down; new submissions are rejected after close.
- Existing blocking `flush()` remains for final reconstruction running on the worker, preserving data consistency where it is required.
- No storage/schema/dependency changes.
