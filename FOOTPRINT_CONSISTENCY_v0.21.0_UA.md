# ElektorykScan v0.21.0 — Footprint Consistency Pass

Цей прохід закриває залишкові місця, де після переходу на нерегулярні контури код усе ще використовував прямокутний `RoomZone.min/max` як реальну форму кімнати.

## Виправлено
- Додано `RoomFootprint` — єдину footprint-aware перевірку належності точки кімнаті через `floorRuns`, з fallback для старих проєктів без runs.
- Semantic furniture більше не прив'язується до кімнати лише тому, що об'єкт лежить у її bounding box.
- Scanned/unrecognized objects отримують roomId тільки за реальною підлогою кімнати.
- Semantic room classifier більше не може назвати Г-подібну кімнату кухнею/спальнею через об'єкт у порожньому куті bounding box.
- WallPlacementEngine вибирає кімнату за footprint (з допуском 1 cell біля стіни), а не за прямокутною оболонкою.
- StructuralFeatureDetector прив'язує колони/особливості до room floorRuns.
- Digital Twin тепер малює підлогу й стелю з фактичних `floorRuns`. Для Г-/П-подібних кімнат більше немає візуальної «фантомної» підлоги/стелі в порожніх кутах.
- Сітка Digital Twin кліпається реальним footprint.
- Room Kit перед додаванням предмета притягує його до найближчої реально існуючої клітинки кімнати, якщо нормалізована bbox-позиція потрапила в виріз/нішу.
- Duplicate furniture також не може опинитися в порожній частині irregular room після стандартного зміщення 25 см.

## Regression
Синтетичний L-shaped footprint: точка всередині bbox, але поза реальною підлогою, коректно відхилена; nearestInside повернув валідну footprint-cell. Pure Java test: PASS.

## Сумісність
Нових залежностей немає. Java 8. Старі проєкти без `floorRuns` продовжують використовувати bbox fallback.
