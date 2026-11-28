# ElektorykScan v0.17.0 — Precision Fusion

## Мета
Цей прохід робить Smart Scan максимально метрично надійним у межах можливостей Samsung Galaxy S22 Ultra + ARCore Depth. Основний принцип: програма не повинна підвищувати точність просто від кількості кадрів. Геометрія стає сильною тільки коли її підтверджують незалежні джерела: новий Raw Depth кадр, confidence, повторне спостереження з іншої позиції, ARCore plane tracking та узгоджена структура point cloud.

## 1. Джерело глибини
- RAW_DEPTH_ONLY використовується там, де ARCore його підтримує.
- Smoothed/full depth не змішується з Raw на S22 Ultra; це лише слабкий fallback для іншого пристрою без raw precision mode.
- Raw depth приймається тільки для поточного camera frame. Reprojected/stale depth не інтегрується з новою позою.
- Confidence image має відповідати тому самому raw depth timestamp.
- Робочий діапазон обрізаний і зважений: ближня/середня кімнатна геометрія має більшу вагу, далека — меншу.

## 2. Відсів шуму
- Адаптивний confidence threshold залежно від відстані.
- Середня confidence точка мусить мати локально узгоджених depth-сусідів.
- Дуже слабка точка не допускається.
- Спеціальний plane-gated rescue: слабша Raw точка може пройти лише коли одночасно узгоджена з сусідами та лежить біля незалежно tracked ARCore wall/floor/ceiling plane.
- Стрибки пози ARCore за короткий час відкидаються до інтеграції point cloud.

## 3. Multi-view замість “покрутив телефон”
- Voxel запам’ятовує distinct depth frames і реальний baseline між позиціями камери.
- Для фінального raw voxel потрібен помітний фізичний зсув камери; дрібний pose-jitter не рахується другим ракурсом.
- Smooth-only voxel має значно жорсткіші умови.
- LIVE multi-view та фінальна point-cloud confirmation — окремі перевірки.

## 4. ARCore plane fusion
Доданий PlaneEvidenceTracker:
- VERTICAL → незалежне свідчення стіни;
- HORIZONTAL_UPWARD_FACING → кандидат підлоги;
- HORIZONTAL_DOWNWARD_FACING → кандидат стелі.

Підлога/стеля блокуються тільки після кількох узгоджених оновлень та в правдоподібному діапазоні від телефона. Стіл/полиця не повинні автоматично стати підлогою. Plane geometry не заміняє Depth: вона класифікує та підсилює уже виміряні XYZ-точки.

Додатково v0.17.0 більше не довіряє лише `getExtentX/Z()` (це тільки bounding rectangle): копіюється реальний convex `Plane.getPolygon()`. Слабкий Raw Depth може отримати plane-rescue тільки всередині фактично знайденого polygon; біля краю support зменшується, а за краєм не досягає rescue-порогу. Площа plane evidence теж рахується по polygon, а не по прямокутнику. Це зменшує роздування стін біля дверей, кутів і меблів.

## 5. Щільніша геометрія без фризів
Sampling grid Raw Depth зсувається між кадрами. За 9 послідовних depth frames програма дивиться на різні підпозиції всередині тієї самої sampling cell. Так поверхня стає щільнішою без збільшення кількості точок, які обробляються в одному кадрі.

## 6. Camera configuration
- 30 FPS camera configuration — менше зайвого camera/GPU навантаження, стабільніше тривале сканування.
- FIXED focus — пріоритет tracking, без постійного autofocus hunting під час room scan.
- Depth sensor usage навмисно не вимикається: ARCore може використовувати доступне апаратне depth hardware, якщо воно підтримується конкретною конфігурацією.

## 7. Точніший фінальний point cloud
- Raw та smooth мають різну вагу.
- XYZ voxel зберігає confidence-weighted sub-voxel mean, тобто 4 см voxel не означає 4 см квантування координати.
- Вихідна confidence кожної точки залежить від реальної depth confidence, raw/smooth source, кількості незалежних кадрів і baseline.
- Непідтверджені площиною об’єкти не видаляються — меблі, ніші та інші деталі лишаються, але менше впливають на архітектурні межі.

## 8. Точніші стіни та розміри
RoomAnalyzer:
- confidence-weighted robust percentile для початкового envelope;
- confidence-weighted wall histograms;
- sub-bin centroid refinement замість жорсткого центра 4–5 см bin;
- confidence-weighted boundary support.

RoomAxisEstimator:
- confidence-weighted PCA;
- confidence-weighted X/Z wall concentration;
- прибраний hard cap, який раніше міг винагороджувати неправильний кут, коли одна справжня стіна розмазувалась по багатьох bins;
- глобальний пошук 1° + локальне уточнення 0.1°;
- tracked vertical ARCore planes формують незалежний dominant wall-yaw hint через circular mean з 90° симетрією. Він використовується тільки як tie-breaker: якщо point cloud має явно кращий fit, навіть 100% неправильний plane hint його не перекручує.

## 9. Підлога і стеля
Коли tracked ARCore floor/ceiling plane стабільний і сходиться з Raw-Depth boundary, їх висоти змішуються. Якщо estimates розходяться занадто сильно, plane hint ігнорується. Це дає незалежний контроль висоти, але не дозволяє випадковому столу переписати кімнату.

## 10. План і площа
- floor footprint cell: 0.25 м → 0.18 м;
- multi-room topology grid: 0.12 м → 0.10 м;
- topology grid визначає зв’язність/наявність стін, але не є фінальною рулеткою;
- `FineWallRefiner` уточнює не лише перпендикулярне положення wall plane, а й ОБИДВА кінці стіни по high-confidence metric point cloud;
- endpoint refinement використовує robust weighted near-extremes і має ±18 см safety-clamp, тому сіткова квантизація прибирається, але неповний/поганий cloud не може раптово вкоротити стіну на пів метра.

## 11. “Макс. точність” після скану
На Smart Scan екрані додана пряма кнопка контрольних замірів. 2–4 довгі відстані лазером або рулеткою дають:
- robust global scale correction;
- outlier rejection між замірами;
- окремий контроль X/Z mismatch;
- RMS та максимальний residual;
- приблизну очікувану похибку плану.

Це важливо: S22 Ultra не є геодезичним лазерним сканером. Контрольний довгий лазерний розмір — найкращий спосіб прибрати систематичну scale-похибку, не підміняючи саму 3D-реконструкцію.

## 12. Як сканувати для найкращого результату
1. Тримати телефон приблизно на рівні грудей/плечей, не біля самої підлоги.
2. Почати з добре текстурованого кута або дверного прорізу.
3. Рухатись повільно, а не крутитись на одному місці.
4. Кожну важливу стіну показати щонайменше з двох позицій зі зміщенням приблизно 20–50 см.
5. Дивитись не тільки на центр білої стіни, а й на стики підлога–стіна, кути, двері, плінтус, розетки, картини/текстури.
6. Окремо пройти низ, середину стін і верх/стелю.
7. Старатись тримати основну геометрію приблизно в кімнатній зоні 0.7–3.5 м; далекі поверхні краще добрати ближчим проходом.
8. Якщо `pose!` росте — повернутись до вже баченого кута/дверей і дати ARCore стабілізувати tracking.
9. Не завершувати тільки тому, що видно багато точок: чекати `multi-view`, balanced low/wall/upper coverage і precision-ready стан.
10. Для фінальних розмірів додати 2–4 контрольні лазерні/рулеточні виміри, бажано довгі й у різних напрямках.

## Перевірки цього проходу
- scanner core: javac зі stubs — PASS;
- reconstruction core: javac — PASS;
- SceneAnalysisPipeline precision overload: javac зі stubs — PASS;
- multi-view voxel regression — PASS;
- synthetic room with low-confidence false outer walls — recovered bounds within ~1–2 cm in fixture;
- synthetic rotated room axis 17° — recovered 17.0°;
- wrong ARCore wall-yaw safety test — 100% неправильний hint не перебив сильну правильну Depth-геометрію;
- ARCore plane polygon gate — center/edge/outside/far cases PASS;
- wall endpoint metric refinement — PASS;
- Manifest Activity mapping / explicit Activity targets / UI button listeners / XML — фінальний validation PASS.

## Межі
Синтетичні тести перевіряють математику й регресії, але не є обіцянкою 1–2 см у реальній кімнаті. Реальна похибка залежить від освітлення, текстури, руху, температури/навантаження, ARCore tracking, відстані та конкретної сцени. Тому програма показує confidence/quality і має окрему метричну калібровку реальними вимірами.
