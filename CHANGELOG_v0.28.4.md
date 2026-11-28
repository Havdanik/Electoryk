# ElektorykScan v0.28.4 — Level Continuity Guard

- versionCode 125 / versionName `0.28.4-level-continuity-guard`.
- Fixed a floor/ceiling continuity regression in `PlaneEvidenceTracker`: the previous candidate selector always preferred the lowest eligible floor and highest eligible ceiling before applying hysteresis. A stair landing, lower adjacent room, mezzanine or ceiling recess could therefore become the challenger on every tracker update and eventually replace a still-visible correct room level.
- When an architectural level already exists, candidates within the incumbent tolerance (18 cm floor / 20 cm ceiling) now have priority. The nearest incumbent candidate wins, with observed area as a near-tie breaker.
- A different-height level can enter the existing multi-frame challenger state only when no credible incumbent-level ARCore plane remains visible in that update.
- Initial acquisition remains unchanged: before a level exists, the scanner still chooses the lowest eligible floor / highest eligible ceiling and then locks it through repeated observations.
- No scene/package schema changes; existing projects remain compatible.
