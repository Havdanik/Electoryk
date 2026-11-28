# ElektorykScan v0.21.3 — Editor topology integrity pass

Focus: keep semantic/visual data consistent when the user manually splits or merges reconstructed rooms.

## Fixed
- `ApartmentPlanEditorEngine.mergeRooms()` now migrates furniture from the removed room id to the surviving room id. Previously furniture could become semantically orphaned after a merge even though it was still visible in 3D.
- Room finishes (floor preset/color/pattern/scale/rotation, ceiling preset/color/pattern/scale, skirting) are preserved across merge and copied to both children on split. Manual topology edits no longer reset half/all of a finished room to RAW defaults.
- `splitRoom()` no longer assigns openings by the opening center. The center lies on the wall and is ambiguous. Affected openings are re-evaluated from samples on both sides of their parent wall, preserving exterior/interior topology more reliably.
- Windows remain explicitly external after split reconciliation.
- Structural features use nearest-child fallback when their center lands on the split boundary instead of being biased to the first child.
- Furniture is reassigned to the correct child after split; if quantization puts an item outside an irregular child footprint, it is moved to the nearest valid footprint cell.

## Regression validation
- Pure-Java compilation of the changed editor engine + required model classes: PASS (Java 8 source/target).
- Synthetic split/merge test: furniture keeps a valid room id, remains inside the assigned footprint, and finish metadata survives split + merge: PASS.
- Synthetic exterior-opening test: an exterior door remains attached to a child room and stays external after splitting the parent room: PASS.
- No new Android/library dependency introduced.

## Build limitation
Full Android Gradle build still depends on the environment having the configured Gradle distribution/dependencies. The changed pure-Java topology path was compiled independently to catch Java-level errors.
