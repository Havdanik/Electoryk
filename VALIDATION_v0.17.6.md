# Validation — v0.17.6

## Статичні перевірки

- Java brace balance: OK для ArSurfaceGridRenderer, ArCoreDepthCollector, ArCoreScanActivity.
- javac parse pass: немає syntax-level помилок; локальне середовище не має Android/ARCore SDK, тому повна компіляція тут неможлива.
- Повний Gradle build запускався, але wrapper потребує завантаження Gradle 8.2; у контейнері немає DNS/інтернету до services.gradle.org.
- Перевірено офіційний ARCore Pose API: використані overload-и transformPoint(..., offset, ..., offset) підтримуються.
- Перевірено офіційний Trackable/Plane API: Plane.createAnchor(Pose) підтримується.

## Регресійні перевірки коду

- У renderer більше немає observeLookRay().
- У renderer більше немає логіки «tile відсутній => червоний весь Plane».
- Coverage надходить із ArCoreDepthCollector.DepthEvidenceFrame після реально прийнятого depth frame.
- Один frameId підвищує independentFrames конкретної клітинки максимум один раз.
- Горизонтальні Plane допускаються лише при floorLocked/ceilingLocked і близькому рівні Y.
- Сітка рендериться відносно Anchor поверхні.
- Finish label «скан придатний» потребує st.finishRecommended AND gridStats.visuallyReady().

## Що треба перевірити на телефоні

- Відсутність гігантської червоної павутини одразу після старту.
- Червоні клітинки з'являються локально по реально зчитаній стіні.
- Після бокового руху ті ж клітинки переходять червоний -> жовтий -> зелений.
- Сітка не зсувається відносно стіни при повороті телефона.
- Ліжко/стіл не приймаються за підлогу після lock рівня підлоги.
- FPS камери не просідає через overlay.
