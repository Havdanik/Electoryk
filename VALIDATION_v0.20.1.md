# Validation v0.20.1

- FurnitureItem / PlanWallSegment / WallFinishCatalog targeted javac: PASS.
- Backward JSON defaults retained for old projects: PASS by source compatibility review.
- DigitalTwinActivity / DigitalTwinView parse check: no syntax diagnostics; Android SDK unavailable for full compile.
- Structural wall geometry remains separate from surfaceStateKey and finishPresetKey.
- Built-in finish catalog: 22 presets.
- Built-in asset catalog expanded with electrical, lighting, TV, furniture, kitchen, appliances, sanitary, mirrors and radiators.
- RAW scan photo remains available when finish preset is RAW_SCAN.
- Decorative finish changes do not overwrite scannedTextureFile or wall geometry.
- ZIP root structure checked: app/, build.gradle, settings.gradle at root.
