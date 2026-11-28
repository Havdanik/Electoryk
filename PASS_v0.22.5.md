# ElektorykScan v0.22.5 — Recovery deadlock + keyframe durability

- Fixed a real deadlock in `ScanVisualKeyframeRecorder.resetPass()`: it no longer holds the recorder monitor while waiting for queued IO. A failed pending keyframe write needs that monitor to roll back metadata, so the previous sequence could freeze when starting a fresh Smart Scan after an IO/ENOSPC failure.
- Added a `resetting` gate so camera callbacks cannot enqueue new RGB/semantic work while a pass is being flushed and its AR-origin evidence is being deleted.
- Every fully committed JPEG/D16 keyframe now immediately persists atomic metadata on the same ordered IO executor. A process kill no longer leaves all keyframes since the last pause/finish orphaned from `keyframes.json`.
- Hardened synchronous `close()`: capture is gated before the final flush, while the internal flush can still drain queued writes and persist metadata.
- Preserved v0.22.1 reusable TFLite buffers, v0.22.2 bounded keyframe backpressure, v0.22.3 atomic JPEG/D16 commit, and v0.22.4 recovery/scavenging.
