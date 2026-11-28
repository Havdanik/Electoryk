# ElektorykScan v0.16.9 — scanner audit

## Симптом від користувача
- Smart Scan підвисає.
- LIVE coverage показує лише 2D footprint, хоча фактична сцена має підлогу, вертикальні стіни та верх/стелю.

## Коренева причина 2D
Point cloud у застосунку вже зберігав X/Y/Z, але `LiveScanCoverage` агрегував endpoint-и лише у `(ix, iz)`. `LiveScanCoverageView` малював цю X/Z map зверху. Тому різні висоти одного місця фізично не могли бути показані окремо.

## Кореневі причини freeze/jank
1. `collector.stats()` викликався на кожному GL render frame; coverage snapshot був дорогим і перебудовувався надто часто.
2. Коли Raw Depth тимчасово не давав валідного кадру, acquire attempt не мав окремого throttle від успішного frame timestamp.
3. До ~тисячі point-cloud inserts/frame робили окремі synchronized add.
4. Періодичний checkpoint / onPause / finish могли копіювати великий cloud у невдалому потоці.
5. Live map створював багато temporary key objects у hot path.

## Виправлення v0.16.9
- Sparse XYZ coverage voxels 28 см.
- Isometric 3D LIVE preview.
- Окремі evidence scores: низ/підлога, стіни, верх.
- Voxel-neighborhood topology для розпізнавання вертикальної безперервності стін.
- Depth processing ~7 Hz; unsuccessful acquire теж throttled.
- Raw sampling target ~900, smooth ~520 endpoint candidates/frame.
- Cached stats/coverage snapshot ~280 ms.
- `VoxelPointCloud.addBatch()` — один lock/frame.
- Primitive long-key XYZ lookup — без key allocation/sample.
- Live display cap 3200 voxels.
- Periodic/onPause checkpoint cap 50k points у worker.
- Final full snapshot/checkpoint у worker.
- ARCore `PlaneFindingMode.HORIZONTAL_AND_VERTICAL` заданий явно.
- `capturePaused` volatile між UI та GL thread.

## Validation
- Core collector compile with ARCore/Android stubs: PASS.
- Live 3D View compile with Android UI stubs: PASS.
- Activity parse check: no Java syntax-pattern errors.
- 39 Java Activity / 39 Manifest Activity: PASS.
- 39 explicit navigation targets: all classes exist.
- Button heuristic: 179 created Activity buttons, 0 without `setOnClickListener`.
- XML parse: PASS.
- 3D coverage regression: PASS.
- 3D topology regression: PASS.
- cached stats regression: PASS.
- synthetic coverage performance test: PASS.

## Межа перевірки
У контейнері немає реального Android camera/ARCore runtime, тому фізичний Depth stream, thermal behavior GPU/CPU та конкретний Samsung camera pipeline потрібно підтвердити з новим APK на телефоні.
