# ElektorykScan v0.28.3 — Architectural Level Hysteresis

- Hardened floor/ceiling tracking against transient horizontal ARCore planes, stairs/landings and furniture.
- Mature floor/ceiling levels no longer jump immediately when a candidate appears >18/20 cm away.
- Added independent floor and ceiling challenger state: a new level must remain spatially consistent for 4 tracker updates and have >=0.55 m² observed area before takeover.
- Conflicting candidates no longer increase confidence of the currently locked architectural level.
- Returning evidence near the locked level cancels the challenger and resumes normal robust smoothing.
- Reset now clears all challenger state; no scene/project schema changes.
