# ElektorykScan v0.22.0 — фінальний релізний прохід

## Що було ціллю
Фінальний прохід не додає декоративні функції заради номера версії. Основна ціль — зробити критичний ланцюжок `Smart Scan → reconstruction → topology → editor → save/export → import/reopen/recovery` цілісним при довгому скануванні, аварійному закритті процесу та переносі FULL-проєкту на інший пристрій.

## Критичні виправлення

### 1. Scene + point cloud тепер одна crash-consistent генерація
Раніше `scene.json` та `points.bin` могли бути записані послідовно. Падіння процесу між двома записами могло змішати нову геометрію зі старою хмарою точок. v0.22.0 спочатку повністю записує та `fsync`-ить обидва staging-файли, фіксує стан PREPARED у journal, після чого робить roll-forward commit. Після crash відкривається або попередня повна генерація, або завершується нова.

Той самий принцип застосований до `checkpoint.json + checkpoint/points.bin`.

### 2. FULL import не змішується зі старими локальними 3D-даними
FULL-пакет є авторитетним. Якщо в ньому є scene+points — вони встановлюються парою. Якщо scene є, але raw cloud відсутній — новий scene комітиться разом із гарантованим видаленням старого `points.bin`. Якщо FULL-пакет взагалі не має scene — старий локальний scan state, checkpoint, visual pass і textures очищаються через відновлюваний journal state.

### 3. Один lock для scanner/editor/export/import
Старий `synchronized` захищав лише один екземпляр `SceneRepository`, але різні екрани створюють різні екземпляри. Тепер використовується process-wide `IO_LOCK`. Це не дає експорту або іншому екрану побачити `PREPARING` commit іншого репозиторію чи прочитати лише половину generation.

### 4. FULL export реально переносить photo wall textures
`SceneSnapshot` зберігав назви baked wall textures, але раніше FULL ZIP не містив самі `scenes/<project>/textures/*`. Тепер referenced textures включаються в checksum і пакет як `scene/textures/*`; імпорт перевіряє safe filename, checksum/limit і відновлює їх. Посилання на відсутню/небезпечну texture очищається замість збереження битого reference. Package schema: 6.

### 5. Recovery зберігає режим “Новий Smart Scan з нуля”
`replacePrevious` тепер записується у checkpoint. Якщо застосунок закрився після clean-pass, `Відновити` не повертає старий point cloud у fusion.

### 6. Lifecycle race та ANR hardening
Recovery більше не ставить reconstruction другим вкладеним `worker.execute()`, який міг бути відхилений між checkpoint load і `onDestroy()`. Тривалий reconstruction завершується на worker, а keyframe/TFLite cleanup очікує його завершення у daemon cleanup thread, не на Android main thread.

### 7. Менше RAM/GC у reconstruction
`RoomAnalyzer` більше не створює великі списки boxed `Float`/`WeightedCoord` і не сортує хмару шість разів заради min/max percentile. Використано primitive packed `long[]` та один sort на вісь. `RoomAxisEstimator` замість boxed `HashMap<Integer,Double>` на кожний candidate angle використовує reusable primitive histogram.

У standalone synthetic JVM benchmark на однаковій хмарі ~370 тис. точок під час цього проходу: v0.21.6 ≈181064 KB peak RSS / 2.77 s; v0.22.0 ≈95864 KB / 1.15 s, при однаковому знайденому yaw 22.32°. Це regression/performance benchmark JVM, а не вимір на Galaxy S22 Ultra.

### 8. Захист від битих imported/recovered plans
`RoomFootprint` перевіряє finite cell/origin, invalid runs, extreme index conversion і NaN/Infinity. `ProjectIntegrityEngine` не падає на room без ID/null entries і додатково перевіряє grid bounds, wall/opening/furniture geometry.

### 9. AR logical origin
Refinement anchor тепер відхиляється не лише при занадто великому translation jump, а й при різкому rotation jump. Це закриває pure-rotation relocalization shock, який translation-only guard не бачив.

## Що не змінювалося
Формат raw point cloud залишився VERSION=1, Android minSdk 26 / targetSdk 34, Java 8, ARCore 1.54.0 та TFLite 2.16.1. Наявні scanner/editor/electrical/interior можливості не видалялися.
