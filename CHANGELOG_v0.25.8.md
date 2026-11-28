# ElektorykScan v0.25.8 — Candidate Pool GC Hardening

- Reused semantic Candidate objects and their fixed robust-depth reservoir arrays across inference frames.
- Added bounded 64-entry candidate pool to prevent unbounded retained memory.
- Candidate reset now clears all statistical accumulators, robust bounds and sample count before reuse.
- Preserved connected-component identity, robust quantiles, multi-view association and geometry behavior.
- Pool is cleared by tracker reset so scan lifecycle ownership remains explicit.
