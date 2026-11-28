# ElektorykScan v0.22.6 — Semantic epoch isolation

Deep-pass finding: an in-flight semantic TFLite inference could survive `resetPass()` and later integrate its old camera pose / Depth evidence into the newly reset AR coordinate frame. That could create phantom/misaligned door/window/wall/object semantics after starting a new Smart Scan or recovery reset.

## Fix
- Added a monotonically increasing scan generation/epoch to `IndoorSemanticSystem`.
- Every inference captures the generation it belongs to.
- `reset()` advances the epoch before clearing the tracker.
- Results and failure accounting from an obsolete epoch are discarded rather than mutating the new scan.
- The single inference executor remains serialized, so the TFLite interpreter is never used concurrently.

## Preserved hardening
- reusable TFLite tensor buffers (0.22.1)
- bounded visual-keyframe backpressure (0.22.2)
- fsync + temporary-file keyframe commit (0.22.3)
- recovery of committed visual evidence (0.22.4)
- reset/IO deadlock fix and per-keyframe metadata durability (0.22.5)
