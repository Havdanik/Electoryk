# ELEKTORYK Scan v0.16.7 — поглиблений аудит цілісності даних

Дата: 23.10.2026

## Що було перевірено цього проходу

Цей прохід продовжує аудит зібраного v0.16.4 APK і виправлення v0.16.5–v0.16.6. Основний фокус v0.16.7 — не кількість екранів, а сценарії, де функція формально працює, але може залишити змішані версії даних, пошкоджений файл, stale-стан або зависнути на великому FULL-пакеті.

## Критичні виправлення

1. **LIGHT 3D consistency.** LIGHT містить `scene.json`, але не містить `points.bin`. Для вже існуючого об'єкта новий LIGHT більше не переписує локальний FULL 3D scene metadata поверх старого point cloud. Локальна повна 3D-пара зберігається цілісною.
2. **Строгий package shape.** LIGHT-пакет відхиляється, якщо всередині є FULL-only point cloud/evidence/audio. `points.bin` не приймається без `scene.json`; evidence/audio/signature мають відповідні index/state JSON.
3. **Streaming FULL import.** Великі бінарні entry розпаковуються у staging-файли на диску з SHA-256 під час потоку. В RAM залишаються тільки невеликі JSON.
4. **Low-storage guard.** Максимальний staged-розмір залежить від фактичного вільного місця кешу. При критично малому запасі імпорт відмовляється до commit.
5. **Path confinement.** `projectId`, evidence/audio leaf names і revision/recovery filenames не можуть вийти за межі папки проєкту через `..`, `/`, `\`, приховані спеціальні імена тощо.
6. **Atomic attachments.** Evidence і великі імпортовані бінарні файли спочатку повністю записуються + `fsync`, а потім атомарно стають робочим файлом.
7. **Review performance/data safety.** ReviewActivity більше не вантажить ~300k point-cloud points і не переписує `points.bin` на кожне рішення. Зберігається лише `scene.json`.
8. **Point-cloud validation.** При записі відсікаються null/NaN/Infinity/абсурдні координати, а header містить реальну кількість валідних точок.
9. **Real backup promotion.** Коли JSON main пошкоджений, але `.bak` успішно парситься, перевірений backup тепер реально повертається на місце main. Це не дає наступному save затерти хороший backup пошкодженим main.
10. **Visual asset recovery.** Каталог моделей також відновлюється з `.bak`; пошкодження main більше не означає тиху втрату імпортованих пресетів і перебудову лише built-ins.
11. **External import confirmation.** Відкриття `.elektoryk` із Telegram/файлового менеджера не запускає commit автоматично — потрібне явне підтвердження.
12. **Export races.** Handover/report/package exports мають унікальні імена, тому новий export не перезаписує файл, який Telegram/Drive ще читає.
13. **Consultation recorder lifecycle.** Service став `START_NOT_STICKY`; одночасний запис двох об'єктів відхиляється; UI показує, що запис іде в іншому об'єкті; stop/finalize cleanup виконується у `finally`.
14. **Consent revocation.** Якщо згоду на аудіозапис забрати в брифі, активний запис саме цього об'єкта зупиняється негайно; auto-record без згоди не зберігається як активний.
15. **Hard process kill.** На старті нового app process прибирається stale recording marker, який фізично не може відповідати живому recorder після смерті попереднього процесу.

## Статична валідація після правок

- 198 Java-файлів.
- 39/39 Activity мають source + Manifest declaration.
- 38 статично знайдених Activity class-targets: 0 відсутніх.
- 166 Activity-local Button variables: 0 без `setOnClickListener`.
- 0 Activity без `setContentView`.
- 9 XML: 0 parse errors.
- `javac -proc:none` по всьому source: 0 syntax-like errors; очікувані unresolved symbols спричинені відсутністю Android SDK/ARCore/org.json у цьому середовищі.
- Pure Java integrity helpers compile: PASS.
- Synthetic atomic/backup/path/stream-limit test: PASS.

## Межа перевірки

Я не позначаю v0.16.7 як «перевірено на реальному Android», бо в цьому середовищі немає Android SDK/ADB/емулятора/реального ARCore Depth, Bluetooth і мікрофона. Попередній APK-аудит підтвердив відповідність вихідної кодової бази зібраному v0.16.4 APK; цей прохід виправляє та валідовує source-рівень. Наступний максимально корисний крок — зібрати v0.16.7 у AIDE та дати новий APK на повторний APK/Dex/Manifest аудит і реальне device-тестування з твого боку.
