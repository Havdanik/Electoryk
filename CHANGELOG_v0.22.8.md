# ElektorykScan v0.22.8 — Shutdown Keyframe Drain

- Fixed a lifecycle data-loss race in `ScanVisualKeyframeRecorder.close()` / `closeAsync()`.
- `closed=true` already prevents any late camera callback from entering the final submit gate.
- The previous shutdown code additionally incremented `passEpoch`. Pending JPEG/D16 jobs had already been accepted by the current pass, but they re-check that epoch on the IO executor; shutdown therefore discarded valid final keyframes instead of draining them.
- Shutdown now rejects new captures while allowing already accepted keyframe writes to complete in executor order, followed by the final metadata commit.
- `resetPass()` still advances the epoch, because a new AR origin intentionally invalidates old-pass queued/callback work.
- No project format changes.
