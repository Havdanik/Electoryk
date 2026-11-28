# ElektorykScan v0.22.4 — Recovery Visual Evidence

- Recovery after Activity/process death now restores already committed RGB keyframe metadata instead of reconstructing a checkpoint with no photo evidence.
- Restored entries are validated against actual JPEG files; missing/corrupt depth companions are downgraded safely to RGB-only instead of poisoning texture baking.
- Keyframe paths are canonical-path checked before reuse.
- Capture serial resumes after the highest committed `kf_NNN.jpg`, preventing accidental overwrite if a recorder is recreated before a fresh-pass reset.
- Stale `.part` files left by a killed process are scavenged when the recorder opens and after failed keyframe commits.
- Fresh Smart Scan behavior is unchanged: `resetPass()` still deletes the previous visual pass so poses from different ARCore origins can never be mixed.
- Based on v0.22.3 atomic JPEG/D16 commit and v0.22.2 bounded keyframe write backpressure.
