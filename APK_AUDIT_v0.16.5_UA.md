# ELEKTORYK Scan — APK audit / stability pass

Версія після проходу: **0.16.5-audit-stability** (`versionCode 20`).
База перевірки: `ElektorykScan_AIDE_v0.16.4-debug.apk` + `ElektorykScan_AIDE_v0.16.4_ANDROIDX_FIXED.zip`.

## Що перевірено саме в APK

- APK успішно розібрано як ZIP/Dex-контейнер; присутні 9 DEX-файлів.
- У DEX знайдено 863 class definitions, з них 599 у namespace `com.elektoryk.scan` (разом із внутрішніми/анонімними/R-класами).
- У вихідному проєкті v0.16.4 є 196 верхньорівневих Java-класів — **усі 196 знайдені в APK DEX**.
- У вихідному коді є 39 `*Activity.java`; **усі 39 є в Manifest**, і немає зайвих Activity у Manifest.
- 38 міжекранних Activity-targets, знайдених статично у переходах, ведуть на існуючі та зареєстровані Activity.
- Усі Activity мають `setContentView(...)`.
- Перевірено 177 явно створених `Ui.button(...)` / `Ui.secondaryButton(...)`: **0 кнопок без `OnClickListener`**. Нижня навігація перевірена окремо — її 5 пунктів мають централізований listener у `Ui.addNavItem`.
- В APK/проєкті присутній справжній ARCore-модуль; Smart Scan використовує ARCore Depth/Raw Depth/Confidence, а не статичну заглушку.
- Водночас Camera2/IMU fallback у v0.16.4 не будує 3D-геометрію. У 0.16.5 це явно позначено як **діагностичний режим**, щоб UI не створював хибного враження.

## Реальні проблеми v0.16.4, які виправлено

1. **LIGHT transfer / phantom files** — індекс міг містити фото або аудіо, яких реально нема після переносу. Імпорт тепер звіряє JSON-посилання з реальними бінарними payloads/local files.
2. **Частковий імпорт при I/O failure** — додано pre-import FULL recovery та rollback; новий частково створений проєкт прибирається при помилці.
3. **Checksum integrity** — schema 5 тепер не дозволяє неперевірені payload entries; duplicate/unsafe ZIP entries блокуються.
4. **Пам'ять на імпорті** — введений device-safe decompressed limit, щоб великий/пошкоджений пакет завершувався помилкою замість OOM; entry-count піднятий до 1024 для реальних проєктів із доказами.
5. **Старий підпис клієнта** — «Очистити підпис» раніше очищало тільки canvas; старий `signature.png` міг лишитися і знову потрапити в export. Тепер файл реально видаляється, metadata/timestamp узгоджуються.
6. **Approval timestamps** — при скасуванні погодження timestamp тепер обнуляється; при повторному погодженні створюється новий час.
7. **Старий signature/point-cloud після import** — stale binary files узгоджуються з новим станом; FULL import більше не змішується зі старим `points.bin`.
8. **PNG share на Android 8/9** — раніше файл зберігався у app-specific storage, але фактично не передавався іншим програмам. Тепер використовується `ExportFileProvider` URI.
9. **Share URI grants** — project/PDF/HTML/PNG/labels отримали `ClipData` + read grant для кращої сумісності з Telegram/Drive/chooser apps.
10. **Cable label OOM / silent failure** — замість одного гігантського Bitmap етикетки діляться на листи по 8 CUT-відрізків; помилка більше не проковтується. Рендер виконується у background task.
11. **Evidence multi-import freeze** — копіювання кількох фото перенесено з UI thread у `StableTaskRunner`; статус «Додано: N» більше не зникає після rebuild UI.
12. **Estimate PDF freeze** — PDF формується у background task; додано перевірку порожнього файлу та безпечніше обрізання довгих рядків.
13. **Asset-pack import** — імпорт JSON перенесено у background, додано max size/count, sanitization ID та габаритів; save/read errors пишуться у stability diagnostics.
14. **Foreground consultation recorder** — старт service тепер guarded; recorder error записується і показується в Object Flow; Android 13+ один раз запитує notification permission, але відмова не блокує сам запис.
15. **Bluetooth printers** — при denied `BLUETOOTH_CONNECT` екран більше не виглядає так, ніби «просто немає принтерів»; показує конкретну причину.
16. **Editor autosave** — помилки auto-save у Digital Twin / Apartment Editor більше не ковтаються безслідно, а потрапляють у stability log.
17. **Fallback scan semantics** — Camera2/IMU режим прямо позначений як diagnostic-only і не видає себе за готовий 3D scan.

## Фінальна статична валідація 0.16.5

- Java delimiter/string/comment sanity check: **0 проблем**.
- `javac` parser pass по змінених Java-файлах: **0 syntax-like errors** (повна Android-компіляція в цьому середовищі недоступна без Android SDK/dependencies).
- Manifest XML: parse OK.
- 39 Activity source / 39 Manifest: OK.
- 38 Activity navigation targets: 0 missing.
- 177 named buttons: 0 unwired.
- 0 Activity без `setContentView`.

## Що неможливо чесно підтвердити тут

У цьому робочому середовищі немає Android SDK, `adb`, Android emulator/device та доступу до реальних Camera/ARCore/Bluetooth сенсорів. Тому **фізично натиснути кожну кнопку у запущеному APK, пройти системні permission dialogs, реально просканувати кімнату, підключити Bluetooth-принтер або записати мікрофон тут неможливо**. Це означає, що статична перевірка дуже широка, але не є заміною instrumented/device test.

Спроба повної Gradle-збірки тут також блокується відсутністю Android SDK/локально закешованого Gradle/залежностей та мережевого resolve. Проєкт залишено AIDE/Gradle-сумісним; збирати APK треба у твоєму AIDE або середовищі з Android SDK.

## Важливий висновок

v0.16.4 не виглядає як «набір намальованих екранів»: основні модулі, переходи, ARCore Depth collector, reconstruction, repositories, transfer, estimate, field workflow, approvals, evidence та printing реально присутні в APK. Найбільші знайдені ризики були не «кнопка взагалі не підключена», а **edge cases з файлами, імпортом, stale state, permissions, background I/O, OOM та тихими помилками** — саме їх і закрито в 0.16.5.
