# Smart Scan v0.16.9 — 3D LIVE + freeze fix

## Що було неправильно

У v0.16.8 реальна point-cloud вже була 3D, але **LIVE COVERAGE залишався X/Z-картою зверху**. Тому точки підлоги, стіни та верхньої частини кімнати, які мають однакові X/Z, візуально складались у той самий 2D-квадрат. Користувач не міг бачити, чи реально просканована висота кімнати.

Окремо `collector.stats()` викликався на кожному GL frame. Усередині він будував coverage snapshot, проходив усю coverage-map і шукав gap-и. Навіть при UI refresh 250 ms важкий розрахунок міг виконуватись близько частоти рендера. Якщо Raw Depth тимчасово не давав кадру, acquire міг повторюватися майже на кожному render frame. Також checkpoint/full snapshot могли копіювати великий point cloud на UI/GL thread.

## Що змінено

### 1. Справжній 3D live coverage
- `LiveScanCoverage` тепер має окрему sparse XYZ voxel-map (`VOXEL_M = 0.28 m`).
- Для кожного прийнятого Depth endpoint зберігаються X/Y/Z, confidence і кількість різних Depth frames.
- 2D X/Z map залишена лише для навігаційних gap/target-підказок.
- `LiveScanCoverageView` відображає XYZ voxels в ізометричній проєкції. Вертикальна стіна реально піднімається по Y, а підлога лежить площиною.
- Для band classification додана voxel-neighborhood topology: вертикальна безперервність має пріоритет як WALL, тому низ стіни не маскується під підлогу тільки через висоту.
- У live UI окремо видно `підлога / стіни / верх`.

### 2. Прогрес не можна набити одними стінами
3D coverage формується з окремих evidence scores:
- підлога — 26%;
- стіни — 54%;
- верх/стеля — 20%;
плюс gate від реального переміщення телефона.

Finish recommendation також вимагає мінімуму для всіх трьох зон, а не тільки великої загальної point-cloud.

### 3. Прибрані основні причини freeze/jank
- Depth acquire/processing: приблизно до 7 Hz.
- Throttle застосовується навіть до невдалого acquire.
- Raw frame: орієнтовно до ~900 sampled pixels; smooth fallback ~520.
- `stats()` має кеш ~280 ms і не перебудовує snapshot 60 разів/с.
- point-cloud додається `addBatch()` одним synchronized блоком на Depth-frame.
- Hot-path XYZ lookup переведений з object-key HashMap на fixed primitive `long -> Voxel` table, щоб не створювати key-object для кожної Depth-точки та зменшити GC-jank.
- live 3D snapshot має display cap 3200 voxels.
- recovery checkpoint бере до 50k points і робиться у worker thread.
- final full snapshot/checkpoint також винесені з UI thread.

## Перевірки
- Pure Java compile: `ArCoreDepthCollector + VoxelPointCloud + LiveScanCoverage + DepthProjectionMath` — PASS.
- UI view compile зі stub Android API — PASS.
- Regression: floor/wall/upper присутні окремо, `maxVy > minVy` — PASS.
- Stationary scan не завищує coverage — PASS.
- Після реального camera travel coverage росте — PASS.
- Stats cache повертає cached object між refresh windows — PASS.
- 3D topology test: floor=40, wall=70, upper=30; вертикальний lower-wall не переходить у floor — PASS.
- Synthetic performance test: 90k Depth endpoints + регулярні snapshots, display cap <= 3200 — PASS.

Фізичний ARCore/Depth тест усе одно потрібно виконати на телефоні, бо контейнер не має Android camera/ARCore runtime.
