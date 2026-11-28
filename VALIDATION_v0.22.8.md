# Validation v0.22.8

Static regression focus: lifecycle shutdown vs scan-pass reset.

- New capture after close: blocked by `closed` fast gate and final synchronized gate.
- Camera callback already converting during close: blocked by final `closed` gate before semantic/keyframe submission.
- Keyframe IO already accepted before close: allowed to finish and persist.
- `closeAsync`: single-thread executor preserves `JPEG/D16 writes -> metadata persist -> shutdown` ordering.
- `resetPass`: still increments `passEpoch`, intentionally invalidating old AR-origin callbacks/jobs.
- Existing backpressure, atomic `.part` commit, recovery metadata, semantic epoch and visual epoch protections retained.
