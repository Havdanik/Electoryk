# ElektorykScan v0.27.7 — Architectural plane priority

- Fixed ARCore plane-evidence selection depending on Collection iteration order.
- The tracker now evaluates all tracked planes and retains the 28 strongest architectural candidates instead of the first 28 returned by ARCore.
- Large walls receive a modest structural priority so fragmented furniture/shelf planes cannot crowd them out.
- Floor/ceiling reference-area gates are recomputed only from retained candidates; discarded transient planes can no longer suppress a valid floor/ceiling lock.
- Existing Depth metric geometry, polygon gates, floor/ceiling stabilization and hot-path allocation-free matching remain unchanged.
