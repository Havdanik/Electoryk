# Validation v0.20.0

- DigitalTwinView targeted Java compile with Android/model stubs: PASS.
- DigitalTwinActivity targeted Java compile with UI/model/repository stubs: PASS.
- Orbit / pinch / pan source path: checked.
- Cutaway chooses wall crossing camera-to-focus line before nearest-wall fallback.
- Furniture drag uses floor ray + 5 cm snap.
- Opening edit preserves center/orientation and changes width symmetrically.
- Undo/Redo serializes both ApartmentPlan and FloorPlan.
- Existing wall placement / electrical wall-lock code retained.
- No new Activity or manifest dependency required.
- Full Gradle Android build not executed in this environment because Android SDK/Gradle dependencies are unavailable.
