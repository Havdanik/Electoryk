# ElektorykScan v0.28.0 — RAW exterior opening topology

## Deep-pass finding
v0.27.8/v0.27.9 hardened doorway traversability by requiring directly observed `rawFloor`, but the helper that decided whether a wall was exterior still used the inpainted/final floor mask (`g.isFloor`). That left an evidence-mixing loophole: an interior partition occluded by furniture could be classified as an exterior edge after footprint filling, and exterior-door rules then allowed only one raw-floor side.

## Fix
- Exterior wall evidence used by automatic door/window promotion now comes only from directly observed `rawFloor`.
- Samples a 10–30 cm band on each side of the structural wall so one-cell axis snapping or consolidation error does not lose the real floor.
- Requires repeated support along >=60% of sampled wall positions and allows only <=18% opposite-side leakage/noise.
- Requires at least three longitudinal samples before a wall can receive exterior privileges.
- Pair-gap doors, continuous-wall doors, and geometry-only windows now share this conservative exterior evidence.
- Filled/inpainted floor remains available for room topology and visualization, but can no longer grant relaxed opening-classification privileges.

## Compatibility
No scene schema, dependency, minSdk, asset-library, editor, import/export or sync format changes.


# v0.22.0 — Final Release Hardening

- Added crash-consistent paired commits for `scene.json + points.bin` and scan checkpoints using a durable PREPARING/PREPARED journal with roll-forward recovery.
- Added authoritative FULL-import journal states so a package with no point cloud or no scene cannot leave stale local scan generations mixed into imported data.
- Added one process-wide `SceneRepository.IO_LOCK`; scanner, editor, diagnostics, export and import can no longer race through separate repository instances.
- FULL project export/import now includes referenced baked wall textures (`scene/textures/*`); missing/unsafe texture references are sanitized on import. Package schema is now 6.
- FULL import applies scene metadata + raw point cloud as one validated pair without decoding the binary cloud into hundreds of thousands of Java objects. Imported point-cloud header/count/length are validated before commit.
- `hasPointCloud()` / checkpoint presence now require a structurally valid point-cloud file instead of only checking that a file exists.
- Preserved the Smart Scan `replacePrevious` / “scan from zero” mode inside the recovery checkpoint, so crash recovery cannot accidentally fuse the old scan back in.
- Removed the nested reconstruction submission after checkpoint recovery, closing an `onDestroy()` / executor-shutdown race that could strand a valid checkpoint.
- Final reconstruction teardown now waits off the UI thread for the worker before keyframe/TFLite resources are closed; UI callbacks are lifecycle-guarded.
- Added AR logical-origin rotation shock protection in addition to translation shock protection.
- Reworked `RoomAnalyzer` weighted robust ranges to primitive packed arrays and one sort per axis; removed large boxed `Float`/helper-object churn.
- Reworked `RoomAxisEstimator` candidate histograms to reusable primitive open-addressing tables instead of boxed `HashMap` traffic.
- Hardened `RoomFootprint` against malformed imported grid values, invalid runs, NaN/Infinity and index overflow.
- Hardened `ProjectIntegrityEngine` so malformed/null room data is reported rather than crashing diagnostics; added grid-run, wall/opening/furniture numeric validation.
- Recovery of a successfully reconstructed checkpoint now counts as a completed scan pass in Object Flow.
- versionCode 61; versionName `0.22.0-final-release-hardening`.

# v0.20.4 — Stability / memory deep pass

- Fixed per-AR-scan executor leak: RGB/depth keyframe writer now has an explicit lifecycle and is closed when Smart Scan is destroyed.
- Fixed semantic model provisioning executor leak: `SemanticModelManager` is now closed by `IndoorSemanticSystem`.
- Bounded Digital Twin scanned-wall bitmap cache to 32 MiB with access-order eviction; prevents many baked wall textures from accumulating until OOM.
- Digital Twin now recycles its texture cache when detached from the window.
- Preserved pause behavior: keyframes are flushed, not closed, on `onPause`, so returning to the scan can continue safely.
- Kept all v0.20.3 compile-hotfix behavior and existing scanner/reconstruction/editor/transfer features.

# v0.20.0 — 3D Workspace Editor

- Повністю перебудований Digital Twin: тепер це робочий 3D workspace, а не декоративний preview.
- Камери ORBIT / WALK / TOP. Orbit: 1 палець — обертання, pinch — zoom, 2 пальці — pan.
- Архітектурний cutaway: у Orbit/Top автоматично ховається саме стіна між камерою і центром сцени.
- Режими PHOTO / SOLID / WIRE без дублювання ApartmentPlan.
- Показ/приховування стелі, метричної сітки 0.5 м, меблів і електроточок.
- Тап по стіні / дверях / вікну / проходу / меблях / електроточці відкриває предметний інспектор.
- Стіна: товщина, матеріал, чистове оздоблення й товщина шару.
- Меблі/об'єкти: ширина, глибина, висота, поворот, крокове переміщення 5 см, drag по підлозі, видалення.
- Отвори: тип DOOR/WINDOW/PASSAGE/UNKNOWN, ширина, висота, висота низу, видалення.
- Електроточки зберегли точний wall-lock редактор; drag лишається метричним.
- Undo/Redo (до 30 станів) одночасно для ApartmentPlan + FloorPlan.
- Фокус на вибраному об'єкті та Frame All.
- Меблі рендеряться суцільними 3D-об'ємами замість дротяних коробок; виділення має XYZ gizmo.
- Двері/проходи рендеряться як темні вирізи, вікна — як окремі напівпрозорі отвори.
- RAW wall texture як і раніше використовує один UV atlas без повторення wallpaper.
- versionCode 41; versionName 0.20.0-3d-workspace-editor.

# v0.19.3 — Recovery & Consistency

- 950 ms tracking-recovery quarantine: after ARCore relocalization, camera pose must remain TRACKING before new Depth geometry is accepted.
- Existing room memory remains visible during recovery, but no unstable first-frame Depth can mutate the scan.
- Live semantic focus is invalidated on tracking loss without deleting persistent semantic entities.
- Stable-ready now requires a fixed coarse topology signature (wall orientation/offset/size), not only a stable wall count.
- Mature semantic objects use repeated-confirmation bounds expansion: a large new extent needs 3 consistent observations before it can grow a confirmed object.
- Readiness is blocked while the logical origin Anchor is in fallback mode.

# v0.19.2 — Long Scan Stability

- mature surface freeze / gradual confirmed expansion
- canonical surface reacquire after small tracking drift
- stale red tile pruning
- semantic entity dedup + weak entity pruning
- stronger mature semantic type hysteresis
- committed-only semantic snapshot/export
- 3 s stable-ready hold with topology wall-count hold

## v0.19.1 — Stable Semantic Scan

- Temporal hysteresis for the live AI label: a single bad frame can no longer rename what the camera is looking at.
- Persistent semantic entities keep per-type votes; compatible furniture labels switch only after repeated evidence.
- Semantic entity bounds expand gradually so one bad Depth frame cannot swallow half the room.
- DepthSurfaceTracker rejects out-of-order evidence and scan-local camera pose shocks/implausible speed.
- Robust 5.5–94.5 percentile frame bounds prevent isolated Depth spikes from permanently stretching a wall.
- Yellow coverage now requires a real baseline/view change; repeated frames from the same phone position stay red.
- Architectural surfaces need more independent frames/support before becoming stable.
- Live overlay locks to one active surface with hysteresis and rejects grazing views, reducing flicker and grid hopping at corners.
- Old persistent grid memory is rendered much fainter; fresh current-view evidence remains dominant.
- TFLite inference has monotonic timestamps, throttling, stale-result rejection and automatic interpreter recovery after repeated failures.
- Regression tests: noisy single wall remains one track, pose shock is rejected, second real wall remains separate, center semantic label resists one-frame flicker.

## v0.19.0 — Semantic Scene AI

- Added on-device indoor semantic segmentation fused with ARCore Depth: RGB answers what the camera sees; Depth remains the metric source for position, shape and dimensions.
- Added persistent SemanticEntity scene memory for walls, floor, ceiling, door, window, mirror, furniture/appliances and generic objects. Repeated views refine the same entity instead of creating per-frame duplicates.
- Semantic masks are split into connected components before Depth fusion, so two disconnected cabinets/chairs of the same class remain separate objects.
- Added dual-model provisioning: full TensorFlow DeepLab ADE20K model plus MLPerf 32-output fallback. Each has its own correct label mapping. TensorFlow DeepLab's 151 outputs are handled as 0=ignore and 1..150=ADE20K classes; MLPerf output 31 is OTHER.
- Semantic inference runs independently of texture keyframes at about 2–3 Hz and continues after the RGB texture-keyframe budget is full.
- Live scanner now reports what AI believes the camera is looking at and combines semantic confidence with structural surface dimensions.
- Door/window semantic components can classify or recover geometric openings by projecting their RGB masks onto the specific reconstructed wall.
- Added explicit PASSAGE type: a floor-level opening crossed by the scan path without door/window semantic confirmation remains an open passage, not a fabricated door.
- Stable semantic objects can be promoted into editable Digital Twin furniture with metric Depth bounds. Unknown/rare classes remain generic OBJECT instead of being hallucinated as a wall or a specific object.
- SceneSnapshot persists semantic entities so later 2D/3D/electrical workflows share the same scene understanding.
- Added TensorFlow Lite runtime dependency and INTERNET permission for asynchronous first-run model provisioning; geometry-only scanning remains available if AI model provisioning fails.

## v0.18.2 — Clean Surface Scanner

- Live overlay no longer renders the entire persistent room model as an x-ray cage. Only the one or two surfaces actively observed by current Depth are shown; stored coverage remains in scan-local memory and reappears when that surface is revisited.
- Old red one-frame tiles are not permanent clutter. Recent tiles render strongly; older confirmed yellow/green coverage on the active surface is only a faint memory layer.
- Added geometric track consolidation: duplicate parallel fits of one physical wall merge back into one persistent Surface ID.
- Vertical RANSAC no longer borrows floor/ceiling cross-section points to make a wardrobe/cabinet side look like a full-height wall.
- Architectural live-wall classification is stricter and validated by room-scale vertical span; synthetic wall+wardrobe regression now keeps the wardrobe non-architectural.
- 2D editor now draws actual `plan.walls` as walls. The phone trajectory is a faint diagnostic line instead of being visually mistaken for geometry. A simple four-wall room gets a clean perimeter fill instead of staircase occupancy cells.
- RGB wall keyframes now save a synchronized coarse Depth buffer projected from accepted scan-local points. Wall texture baking rejects frames where furniture/curtains/people are physically in front of the wall.
- Texture selection prefers adjacent patches from the same keyframe, rejects more oblique views, and uses Depth consistency to reduce patchwork.
- RAW wall atlases are transparent PNGs: unknown/unobserved areas show the neutral physical wall instead of grey JPEG blocks.
- Completion recommendation is stricter: plausible 3–6 structural walls, a horizontal architectural reference and stronger confirmed coverage are required.
- Live metrics show active surfaces/current-view tile counts rather than thousands of historical cells, making diagnostics readable.

## v0.18.1 — Coherent Room Model

- Fixed the impossible opening threshold (`support >= 2` while support is 0..1); doors/windows can now actually pass geometric validation.
- Added direct opening detection inside a continuous perimeter wall, so sanitizing the wall topology no longer erases doors/windows.
- Added final RoomPerimeterSanitizer: one-room scans cannot expose 7–30 furniture-derived walls; implausible topology falls back to the robust outer perimeter and remains low-confidence until re-scanned.
- Live surface memory is persistent; scanned tiles no longer disappear after 1.1 seconds.
- Added an explicit “Новий Smart Scan з нуля”: clean passes no longer silently fuse the old saved point cloud back into the result; “Досканувати” remains the intentional multi-pass path.
- Live architectural surface set is deduplicated before rendering/quality metrics; small cabinet/jamb planes no longer count as stable walls.
- Completion now requires a plausible wall set plus a horizontal architectural reference and stronger confirmed coverage.
- RAW Digital Twin texture no longer repeats one RGB patch like wallpaper. Each wall is baked as a metric multi-keyframe mosaic, with oblique views rejected.
- Final plan confidence now uses wall support, not just wall count; inferred perimeter walls are not presented as a completed scan.

# v0.17.4 — wall geometry / scan-space integrity fix

- Fixed a reconstruction bug where one physical wall was expanded to neighbouring 10 cm grid rows/columns and then persisted as multiple parallel Digital Twin walls. Wall-line extraction now uses the actual wall evidence and collapses residual 1–2 cell thickness bands.
- Removed unsafe scan-origin rebasing while the origin Anchor is PAUSED. v0.17.3 could absorb real phone movement into the origin and leave already accumulated voxels in the old coordinate frame, producing layered/shifted geometry.
- Kept ARCore Anchor refinements only while the Anchor is TRACKING; temporary Anchor loss now keeps the last logical scan origin instead of chasing the camera.
- Clarified RAW+Depth UI status when AUTOMATIC depth mode is active; the old “RAW PRECISION” label could be shown even while smooth fallback frames were being used.
- Clarified that repeated raw-depth timestamps are reprojected data and must not count as independent multi-view evidence.

# v0.17.3 — Live Self-Heal / Freeze Fix

- Fixed the real S22 freeze shown in field screenshots: Camera stayed TRACKING while the scan origin Anchor became non-TRACKING; v0.17.2 then stopped calling `collector.collect()` entirely.
- The origin Anchor is no longer a hard gate. A logical scan origin continues collecting Depth even while an Anchor is PAUSED.
- Added automatic origin rebase that preserves the current local camera pose, so already scanned XYZ geometry is not reset when a replacement Anchor is created.
- Live telemetry/coverage statistics moved off the GL camera thread.
- `VoxelPointCloud.confirmedSize()` is now O(1) via an incremental confirmed-voxel counter instead of scanning the full cloud every UI refresh.
- Periodic recovery checkpoint cadence relaxed to 60 s to reduce contention during long scans.
- Live coverage capacity increased 14k -> 28k voxels while rendered preview payload reduced 3200 -> 2200 points.
- Added `live N%` capacity telemetry so saturation can be diagnosed directly from a screenshot.
- Depth Stream Fix from v0.17.2 and install compatibility from v0.17.1 remain intact.

# v0.17.2 — Depth Stream Fix

- Fixed S22 Ultra regression where Smart Scan stayed at 3D 0%, RAW 0 while `stale` grew continuously.
- Raw Depth new-frame detection now uses Raw Depth timestamp de-duplication instead of requiring exact Frame timestamp equality.
- Smoothed Depth fallback uses its own timestamp de-duplication and is no longer blocked by RGB/Depth timestamp skew.
- Prefer ARCore `DepthMode.AUTOMATIC` when available so Raw Depth remains primary while smoothed Depth can recover temporary gaps.
- Added `RAW / smooth / dup / Δts` live diagnostics.
- Preserved v0.17.0 precision fusion and v0.17.1 legacy JNI install compatibility.

# v0.17.1 — INSTALL COMPAT

- Android 15/16 install compatibility pass.
- ARCore native libraries now use legacy/compressed JNI packaging (`useLegacyPackaging true`).
- Avoids relying on 4 KB ZIP alignment of uncompressed `.so` files produced by AGP 8.2.x.
- Scanner/reconstruction logic is unchanged from v0.17.0 Precision Fusion.
- versionCode: 26; versionName: 0.17.1-install-compat.

# v0.17.0 — Precision Fusion / maximum Smart Scan accuracy

- Raw Depth is treated as the primary metric source; smoothed depth remains a weak fallback only on devices where RAW mode is unavailable.
- Raw Depth is fused only when it belongs to the current camera frame; mismatched/reprojected depth and confidence timestamps are rejected.
- Added ARCore plane-evidence fusion for vertical walls, floor and ceiling. Plane evidence classifies/validates metric depth; it does not invent geometry by itself.
- Plane fusion now uses the actual `Plane.getPolygon()` boundary and polygon area instead of trusting only the bounding extent rectangle; outside-edge samples cannot rescue weak Raw Depth.
- Floor/ceiling plane lock now requires repeated consistent observations and rejects implausible camera-to-plane ranges, reducing table/shelf false positives.
- Low-confidence Raw Depth can be rescued only when it has local depth support AND agrees with an independently tracked ARCore architectural plane; this helps smooth/blank walls without globally lowering noise thresholds.
- Camera configuration prefers 30 FPS and FIXED focus for stable AR tracking and lower thermal/GPU load; hardware depth usage is not disabled.
- Raw Depth sampling grid is staggered between frames so multiple frames densify the surface without increasing per-frame sample count.
- Final voxels require stronger real camera baseline (multi-view evidence); phone pose jitter alone no longer confirms geometry.
- Voxel confidence now includes original measurement confidence, frame count, baseline and raw/smooth source quality.
- Unsupported furniture/niche geometry is preserved but receives less architectural authority than plane-supported structure.
- Room envelope uses confidence-weighted percentiles; weak ghost/outlier clouds cannot move the wall search window as easily.
- Room boundary histograms are confidence-weighted and use sub-bin centroid refinement rather than fixed 4–5 cm bin centers.
- Dominant room yaw estimation is confidence-weighted and no longer caps strong wall peaks; added 1° global + 0.1° local refinement.
- Tracked ARCore vertical-plane normals provide an independent 90°-symmetric wall-yaw tie-breaker, with safety logic that cannot override a clearly stronger point-cloud fit.
- ARCore floor/ceiling plane heights are safely blended with depth-derived room bounds only when both estimates agree, and dependent surface geometry is synchronized.
- Precision-ready finish recommendation is stricter: balanced floor/walls/upper coverage, more confirmed voxels, more directions and stronger multi-view are required. Force-finish remains available.
- Added live diagnostics for RAW confidence, optimal distance share, stale frames, depth outliers and rejected pose jumps.
- Added direct “Макс. точність • 2–4 контрольні заміри” workflow for laser/tape scale calibration and explicit error estimation.
- Floor footprint grid refined from 25 cm to 18 cm; multi-room topology grid from 12 cm to 10 cm; final wall planes are still refined against metric points.
- FineWallRefiner now refines wall endpoints as well as wall-plane position from high-confidence metric points, removing final 10 cm endpoint quantization with bounded safety clamps.
- Synthetic regression: low-confidence false outer walls no longer drag the recovered room envelope; a rotated 17° room axis is recovered at 17.0° in the test fixture.

# v0.16.9 — 3D LIVE COVERAGE + scanner performance

- LIVE coverage більше не є плоскою X/Z-картою: додана sparse XYZ voxel-модель, тому підлога, вертикальні стіни й верх/стеля відображаються окремо у псевдо-3D.
- Загальний 3D-прогрес тепер балансований по висоті: wall-only скан не може виглядати майже завершеним.
- Finish recommendation вимагає мінімального покриття підлоги, стін і верхньої зони.
- Depth acquisition throttled приблизно до 7 Hz; невдалий Raw Depth acquire теж throttled, щоб недоступний/рідкий Raw Depth не опитувався на 60 FPS.
- `collector.stats()` кешується; важкий coverage snapshot більше не перебудовується на кожному GL-кадрі.
- Raw Depth sampling обмежений орієнтовно ~900 samples/frame, smoothed fallback ~520 samples/frame.
- Point cloud отримує цілий Depth-frame через `addBatch()` одним lock замість сотень lock/unlock на кадр.
- Періодичний та onPause recovery-checkpoint обмежений 50k voxel points і готується у worker thread.
- Фінальний full snapshot + checkpoint перенесені з UI thread у worker thread.
- 3D LIVE snapshot віддає максимум 3200 display voxels, щоб overlay не зависав на великих кімнатах.
- XYZ coverage hot path використовує primitive long-key lookup без тимчасового key-object на кожну Depth-точку; 2D guidance-map семплує половину endpoint-ів, бо вона більше не визначає 3D-прогрес.
- Voxel-neighborhood topology відрізняє вертикальну стіну від горизонтальної низ/верх поверхні краще, ніж просте порівняння Y з висотою камери.
- ARCore plane finding явно встановлено `HORIZONTAL_AND_VERTICAL`.
- Додані regression tests: floor/wall/upper не колапсують по Y; stationary scan не накручує прогрес; stats cache reuse.

# v0.16.7 — data integrity / recovery / transfer hardening

- LIGHT transfer can no longer mix imported `scene.json` with an older local `points.bin`; an existing local FULL 3D scene is preserved as one coherent state.
- FULL/LIGHT package import now validates package semantics in addition to SHA-256 checksums: LIGHT rejects FULL-only binaries; point clouds require scene metadata; evidence/audio/signature payloads require their indexes/state.
- Large FULL binaries (point cloud, evidence, signature, consultation audio) are staged to disk while checksums are calculated instead of being accumulated in RAM.
- Import staging is bounded by actual free cache space; low-storage devices fail before partial commit instead of attempting an unsafe oversized import.
- Imported `projectId` and attachment names are strictly confined to the project directory; `.`/`..`, hidden/special leaves, path separators and unsafe snapshot paths are rejected.
- Evidence attachments are written atomically with size limits; stale evidence/audio references are filtered on load.
- Scene point-cloud persistence filters NaN/Infinity/absurd coordinates and commits an actual valid-point count.
- Review/Clean no longer loads hundreds of thousands of points or rewrites `points.bin` for every classification decision; it edits scene metadata only.
- Revision/recovery snapshots commit atomically and reject unsafe package-file references.
- Repository `.bak` recovery now promotes a verified backup back to the main file, preventing the next save from overwriting a good backup with a corrupt main file.
- Visual asset catalog recovery now uses and restores its atomic backup instead of silently rebuilding built-ins over a corrupt catalog.
- External ACTION_VIEW/ACTION_SEND `.elektoryk` files require explicit confirmation before checksum validation and import.
- Export ContentProvider is read-only, validates export leaf names and honors the receiver's requested OpenableColumns projection.
- Handover HTML exports use unique filenames so a later report cannot overwrite a file another app is still reading.
- Consultation recording now uses `START_NOT_STICKY`, rejects simultaneous recording of two projects, reports the active-other-project state, and safely finalizes/indexes recordings.
- Revoking client recording consent immediately stops this project's active foreground recording and disables auto-record until consent exists again.
- Stale consultation-recording state is cleared after a new app process starts, so a hard process kill cannot permanently leave the UI thinking the microphone is still recording.

# v0.16.6 — deep stability / lifecycle / import hardening

- Fixed first-run ARCore black-screen path after CAMERA permission: session resumes immediately.
- Corrected ARCore/GLSurfaceView pause-resume ordering.
- Camera hardware is optional so a tablet/viewer without a camera can still install/use project transfer and office screens.
- Hardened project IDs against path traversal / unsafe imported identifiers.
- Existing projects are never overwritten unless a recovery package was successfully created first.
- Recovery retention is now per-project instead of globally deleting backups from other projects.
- FULL import now removes stale evidence/audio binary leftovers that are no longer referenced.
- Point-cloud reader caps materialized points, uses correct ceil stride, rejects NaN/Infinity/absurd coordinates and truncated files.
- Approval, object-flow and evidence indexes now recover from atomic .bak files and report corruption.
- Scan session logging no longer silently swallows start/write/close failures.
- Export packages now use unique filenames, preventing a later export/recovery job from overwriting a file while another app is still reading it.
- Recovery snapshot copies commit atomically.
- Transient ARCore resume failures now close the broken session so the next resume can recreate it instead of leaving Smart Scan permanently disabled.
- Camera2 fallback now owns/unregisters its TextureView listener, rejects late callbacks after stop, and releases the preview Surface.
- IMU diagnostic coverage survives Pause → Resume instead of resetting to 0%, and duplicate sensor registration is prevented.
- Project transfer blocks Back while a commit is in progress, avoiding invisible background imports after leaving the screen.
- Successful thermal printing now persists the printed-state inside the background transaction, so leaving/destroying the Activity cannot lose the state update.

# CHANGELOG

## 0.15.0 — Full Object Flow

### Added
- one Object Flow dashboard from client brief to handover
- consent-gated foreground consultation recording + in-app audio log
- multi-pass scan fusion with quarter-turn alignment and safe rejection
- client brief: requested points, budget, work class, visualization choice
- centimeter snap + direct numeric wall height/offset editing
- installable visual asset presets + built-in sockets, switches, lights, panels and furniture
- client comparison screen for sockets/switches/lights/panels
- material-aware point BOM: mechanisms, frames, socket boxes, fixtures and panel enclosures
- procurement states including unavailable/replacement, supplier and actual replacement
- estimate PDF + Bluetooth ESC/POS receipt
- ceiling corridor marking viewer
- crew shifts, hourly pay and project expenses
- revision note `before → after` and approval note
- FULL project package schema 5 with Object Flow state and consultation audio

### Stability
- repeated scan no longer silently replaces the previous pass
- reconstruction and gap analysis use the fused multi-pass cloud
- failed fusion keeps the previous geometry intact
- visual asset choice stays separate from technical point type/routing

## 0.14.0 — Digital Twin

### Added
- unified Digital Twin walkthrough/editor over the real ApartmentPlan
- wall-local electrical placement (`wallFraction`, `wallOffsetM`, wall lock)
- drag existing electrical points directly on a wall
- instant wall metrics: height from finished floor, ceiling clearance, distances to both wall ends
- per-wall finish layer and thickness
- optional FurnitureItem reference objects
- furniture footprints in 2D electrical/client views
- wall finish information in wall elevation
- Project transfer schema 4

### Stability
- no second external 3D renderer dependency; Canvas renderer shares the same plan data
- drag updates stay in memory and commit on gesture finish
- existing wall points are migrated to wall-local coordinates
- 2D wall moves preserve locked point position along the edited wall


## 0.13.0 — Complete Field Suite

### Added
- CommercialLine / CommercialPlan
- CommercialPlanEngine
- CommercialRepository
- global CatalogItem / CatalogState / CatalogRepository
- stock / pack-size / purchase quantity
- configurable material/labor markup and discount
- EstimateActivity
- RevisionSummary / RevisionHistory
- persistent revision FULL packages
- RevisionActivity with delta summary
- EvidenceItem / EvidenceIndex
- EvidenceRepository
- EvidenceActivity
- evidence link to circuit and CUT piece
- ClientApproval / ApprovalRepository
- SignatureView
- ClientApprovalActivity
- signature embedded in VIP HTML
- GapAuditItem / ProjectGapAuditReport
- ProjectGapAuditEngine
- GapAuditActivity
- workflow stages COMMERCIAL + AS_BUILT
- package schema 3
- LIGHT/FULL transfer of commercial/revisions/evidence/approval

### Fixed
- blocked workflow button now truly opens the previous stage
- project estimate does not require a price for client-owned material
- revision snapshots are kept separately from rotating recovery snapshots
- handover readiness now checks estimate, as-built, revisions and approvals

### Preserved
- v0.12 atomic persistence, recovery, commissioning and stability tooling

## 0.16.0 — Field Scan Ready
- Live depth-ray coverage heatmap directly over AR camera.
- Strong / weak / missing cells and next weak-zone target with direction + distance.
- Real-time near / optimal / far depth distance guidance.
- Motion-speed warning, vertical-coverage guidance and raw-depth confidence guidance.
- Scan quality score combines spatial coverage, direction coverage, vertical coverage, distance, confidence and motion.
- Finish guard: warns before ending a weak scan; second press can force finish.
- Dedicated scan UI mode hides nonessential buttons while scanning.
- Pause/resume control for deliberate movement or client conversation.
- 45-second periodic recovery checkpoint for long captures.
- AR origin-anchor tracking guard to reduce corrupted geometry after tracking loss.
- Post-scan gap map now draws the actual scan path and reports distance from path to each weak zone.
- «Rescan whole apartment» replaced by targeted «Dосканувати слабкі зони» workflow.

## v0.16.1 — AIDE BUILD FIX
- FIX: додано відсутній кореневий `gradlew`, через який AIDE завершувався з code 127 ще до компіляції.
- Додано AIDE-safe bootstrap Gradle 8.2: system -> cache -> one-time official download.
- Додано `gradle/wrapper/gradle-wrapper.properties` і `gradlew.bat`.
- Version code 17; version name `0.16.1-aide-build-fix`.

## v0.16.2 — AIDE BUILD FIXED
- fixed missing `./gradlew` launcher that caused exit code 127 before compilation;
- added AIDE-safe Gradle 8.2 bootstrap/cache reuse;
- aligned Android build stack to AGP 8.2.2 + Gradle 8.2 + JDK 17;
- changed compileSdk/targetSdk from 35 to 34 because AGP 8.2 officially supports API 34;
- added distribution checksum verification when `sha256sum` is available.

## v0.16.5 — APK audit + stability pass
- hardened .elektoryk import integrity checks and recovery rollback;
- LIGHT import no longer leaves phantom evidence/audio references when binary files are absent;
- import failures clean partial new copies and try to restore overwritten projects from a pre-import FULL recovery package;
- share intents now attach ClipData read grants for better Android/Telegram/Drive compatibility;
- Android 8/9 PNG export now shares through the app ContentProvider instead of stopping at app-private storage;
- client approval can truly delete an old saved signature and approval timestamps reset correctly when approval is revoked;
- evidence import status survives screen re-render;
- cable label PNG export is paged into safe-sized sheets instead of allocating one giant bitmap;
- recording start is guarded against foreground-service launch failures and exposes the last recorder error;
- Bluetooth print screens show permission-denied state instead of silently looking like there are no printers;
- ARCore screen exposes a clearly marked Camera2/IMU diagnostic fallback when Depth is unavailable (fallback does not pretend to create a 3D scene).
- evidence multi-file import, label rendering, estimate PDF generation and asset-pack import moved off the UI thread to reduce freezes/ANRs;
- installable asset packs now have size/count/dimension validation and no longer fail silently when catalog persistence fails;
- Digital Twin and Apartment Editor auto-save failures are recorded in stability diagnostics instead of being swallowed;
- stale signature files and stale FULL point-cloud payloads are reconciled during import instead of leaking into later exports;
- package entry count is raised for real projects with many evidence files while total decompressed memory remains device-safe.

## v0.16.8 — Smart Scan geometry fix
- Critical ARCore projection fix: Raw/Smoothed Depth pixels are now unprojected with `Camera.getTextureIntrinsics()` scaled to the actual depth-image dimensions, matching Google's Raw Depth reconstruction method. The old `getImageIntrinsics()` + `transformCoordinates2d()` path could distort point geometry.
- New depth-frame de-duplication for smoothed fallback; an old/reprojected image can no longer repeatedly inflate scan counters.
- Direction coverage and scan path are counted only when a frame contributes usable 3D geometry.
- LIVE coverage no longer treats free-space ray traversal or camera visits as scanned surfaces; removes the long fake diagonal trails seen in v0.16.7.
- Surface confidence now requires repeated observations across different depth frames, not many pixels from one frame.
- Coverage progress has an evidence gate based on distinct surface cells + real device movement, preventing high percentages while rotating in one spot.
- Explicit on-screen RAW / smooth / voxel-point counters for scanner diagnostics.
- Live map moved below scanner metrics so it no longer covers the important status text.
- Finish recommendation now also requires a meaningful point-cloud size; analysis rejects captures below 600 voxels.
- Added guidance to move sideways for parallax instead of spinning the phone in one position.
- Added pure-Java regression tests for depth-intrinsics scaling, unprojection, false ray-coverage suppression and movement-gated progress.

## v0.17.5 — AR surface coverage grid
- Прибрано плаваюче бічне вікно `3D LIVE SCAN` з хмарою точок під час проходу.
- Додано `ArSurfaceGridRenderer`: сітка малюється безпосередньо поверх живої камери та прив'язана до ARCore Plane у світових координатах.
- Кожна відстежувана стіна/підлога/стеля має незалежну локальну карту покриття ~24×24 см.
- Колір клітинки: червоний = площина знайдена, але даних мало; жовтий = перший придатний прохід; зелений = повторне/multi-view підтвердження з корисною базою.
- Площини, що надто далеко або майже ребром до камери, не засмічують екран сіткою.
- Кольори лишаються «приклеєними» до тієї самої поверхні під час руху телефона.
- Пауза залишає сітку видимою, але не додає нове покриття.
- Підказки тепер залежать від стану AR-сітки: підійти/змінити кут, досканувати червоне, повторити жовте з іншої позиції, перейти далі після зеленого.
- Текстура покриття кешується й оновлюється тільки при зміні даних; внутрішній полігон площини перевикористовується без масових allocation на GL-потоці.

## v0.17.6 — Depth Tile Grid
- Replaced whole-plane AR coverage painting with accepted-depth tile coverage.
- Added per-surface Plane anchors for stable overlay coordinates.
- Added independent-frame / baseline / view-bin confirmation for red-yellow-green tiles.
- Suppressed unobserved cells completely instead of painting them red.
- Restricted horizontal grid to locked floor/ceiling levels.
- Added grid readiness to finish recommendation.
- Reduced scan-time overlay UI to keep the live camera dominant.

## v0.17.7 — Metric Grid 10 cm
- Coverage tile reduced from 22 cm to 10 cm physical AR units.
- Tile inset reduced 12 mm -> 3 mm and border shader thinned.
- Draw capacity raised to 2200 tiles.
- Live UI labels the grid as 10 cm for an immediate real-world scale check.

## v0.18.0 — Structural Room Model
- Removed the v0.17.8 world-voxel billboard fallback that painted thousands of floating 10 cm squares.
- Added persistent scan-local `DepthSurfaceTracker` with stable WALL/FLOOR/CEILING surface IDs; revisiting a surface strengthens the same track instead of creating a new layer.
- Live coverage renders only tiles that belong to fitted structural planes. Finish readiness now requires at least 3 stable walls plus repeated/multi-view coverage.
- Final reconstruction no longer falls back to occupancy-grid edges as walls. `StructuralWallExtractor` requires long, vertically continuous architectural planes and deliberately returns an incomplete scan rather than inventing walls from furniture/noise.
- Fixed histogram edge handling so real walls lying exactly on room min/max bounds are not dropped.
- Interior short/tall planes are kept as object/residual evidence instead of automatically becoming walls.
- Added `ObjectGeometryPromoter` for large non-wall residual geometry.
- Tightened geometry-only opening detection; uncertain gaps remain UNKNOWN rather than being silently labeled door/window.
- Fixed automatic opening bottom/top Y convention (relative to finished floor) to prevent double-adding floor elevation in 3D.
- Added low-rate RGB scan keyframes with exact scan-local camera poses and intrinsics.
- Added per-wall RGB texture baking for RAW Digital Twin walls; scanned paint/wallpaper/brick can be shown from the real camera material patch instead of translucent gray sheets.
- RAW Digital Twin wall/floor fallback fills are opaque; user-applied finishes still override scanned RAW material.
- Live and final geometry now use the same scan-local coordinate system.
- Scanner no longer reports a weak structural result as complete merely because voxel coverage is high.
- True indoor semantic naming (mirror/door/window/furniture class) is intentionally NOT fabricated from Depth; an indoor RGB segmentation model must be bundled separately for those labels.

## v0.20.1 — Interior Library
- Separate scanned surface state from decorative finish.
- Built-in wall finish presets/patterns and apply-all.
- Expanded built-in electrical and interior object library.

## v0.20.2 — Interior Studio Expanded
- Expanded built-in library to 59 electrical/light + 84 furniture/interior presets.
- Searchable grouped Asset Library.
- Per-room floor/ceiling/skirting finish layers and procedural preview.
- Room Kits: bedroom, living, office, kitchen, bathroom, studio.
- Furniture duplicate, lock, replace-in-place, wall snap.
- Wall custom color (#RRGGBB), pattern scale and rotation.
- Electrical point arrays along wall.
- Backward-compatible editor metadata in RoomZone, PlanWallSegment, FurnitureItem and VisualAssetPreset.
- Fixed invalid TV label string literals in v0.20.1 source.

## v0.20.3 — Compile hotfix
- Fixed AIDE javac errors caused by fractional dp values in ApartmentPlanView and DigitalTwinView.
- Added `Ui.dp(Context, float)` overload; existing integer calls remain unchanged.
- Fixes the three `possible lossy conversion from float to int` errors seen in v0.20.2 build output.

## 0.21.2 — Irregular wall evidence
- Structural wall extraction now recognizes concave exterior walls from one-sided floor evidence, not only global RoomBounds min/max planes.
- L/U-shaped alcove/facade segments therefore use boundary-wall thresholds instead of being incorrectly treated as furniture-prone interior partitions.
- Added conservative asymmetry gating: floor must be consistently present on one side and absent on the other, while ordinary interior objects with floor on both sides do not receive facade privileges.
- No storage schema or dependency changes.

## v0.22.1 — Long-scan memory/lifecycle hardening
- Reused TFLite direct input/output tensor buffers instead of allocating new native ByteBuffers on every semantic inference.
- Tensor buffers resize only when model tensor shape requires it and are released on segmenter close/model reload.
- Closed a lifecycle race where `IndoorSemanticSystem.submit()` could pass the `closed` check, lose to `close()`, get rejected by the executor and leave `busy=true` forever.
- Existing Smart Scan, Depth reconstruction, Digital Twin/editor, electrical/interior library, FULL import/export and crash-consistent scene storage remain intact.
