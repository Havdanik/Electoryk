# ElektorykScan v0.22.7 — Visual epoch isolation

Deep-pass hardening of Smart Scan lifecycle.

## Fixed
- Closed a cross-pass race in `ScanVisualKeyframeRecorder`: a camera callback that had already acquired an ARCore image could continue YUV conversion while `resetPass()` completed, then submit semantic/photo evidence using the previous AR origin into the new scan pass.
- Added a visual `passEpoch` generation token. Reset/close invalidate in-flight callbacks before draining IO.
- Added a final serialized epoch gate before semantic submission and before keyframe metadata allocation.
- Pending IO tasks verify their epoch before expensive bitmap/JPEG work, preventing stale callbacks from recreating files after a reset.
- The fix complements v0.22.6 semantic epoch isolation: stale inference results and stale *late submissions* are now both isolated.

## Compatibility
- No existing scene/project format changed.
- No feature removed.
- AIDE-friendly Java source remains dependency-neutral beyond existing Android/ARCore/TFLite dependencies.
