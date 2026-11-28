# ELEKTORYK Scan v0.16.6 — Deep Stability Audit

База: `ElektorykScan_AIDE_v0.16.5_AUDIT_STABILITY_FIXED.zip`.
Цей прохід продовжує попередній APK/code audit і сфокусований на lifecycle, Camera/ARCore, переносі, recovery та цілісності даних.

## Виправлено

1. **ARCore після першого CAMERA permission**
   - раніше сесія створювалась у `onRequestPermissionsResult`, але не resume-илась одразу;
   - результатом міг бути чорний Smart Scan до повторного входу/згортання;
   - тепер після дозволу виконується повний resume-прохід.

2. **Правильний порядок ARCore / GLSurfaceView lifecycle**
   - resume: `Session.resume()` → `GLSurfaceView.onResume()`;
   - pause: `GLSurfaceView.onPause()` → `Session.pause()`;
   - при transient resume failure зламана Session закривається й буде створена заново при наступному resume.

3. **Tablet/viewer install**
   - камера більше не оголошена `required=true` у Manifest;
   - пристрій без камери може використовувати перенос, кошторис, перегляд та інші офісні екрани.

4. **Project ID / path traversal hardening**
   - додано `ProjectIdGuard`;
   - імпорт відхиляє ID з `/`, `\\`, `.`, `..` та іншими небезпечними символами;
   - `FileProjectRepository` не читає/не зберігає unsafe ID і перевіряє відповідність ID імені JSON-файла.

5. **Безпечне оновлення існуючого об'єкта**
   - overwrite не починається, якщо не вдалося створити recovery package;
   - при помилці імпорт зупиняється до зміни основного об'єкта.

6. **Recovery retention**
   - pre-import recovery більше не обрізається глобально по всіх проектах;
   - ліміт застосовується окремо до конкретного projectId.

7. **FULL import як авторитетний стан**
   - видаляються stale evidence/audio файли, яких вже немає у FULL-пакеті;
   - старі binary leftovers більше не накопичуються і не можуть випадково повернутися.

8. **Унікальні export package filenames**
   - кожен LIGHT/FULL export має власне timestamp/id ім'я;
   - новий export/recovery не перезапише файл, який Telegram/Drive ще читає.

9. **Point cloud reader hardening**
   - correct ceil stride при downsampling;
   - materialized point cap 300k;
   - відхиляються truncated files, NaN/Infinity, абсурдні координати;
   - confidence затискається у 0..1.

10. **Repository recovery**
    - Approval, ObjectFlow, Evidence index тепер пробують `.bak` перед поверненням порожніх даних;
    - recovery/corruption пишеться у stability log.

11. **Camera2 fallback lifecycle**
    - controller реєструє і знімає власний `SurfaceTextureListener`;
    - late callbacks після stop не можуть знову відкрити камеру;
    - preview Surface явно release-иться.

12. **IMU Pause → Resume**
    - пауза більше не скидає coverage у 0%;
    - duplicate sensor registration заблокований.

13. **Scan recorder**
    - помилки старту/запису/закриття JSONL більше не ковтаються мовчки;
    - session filename санітизується.

14. **Project transfer transaction UX**
    - Back блокується, поки import/export commit ще виконується;
    - користувач не може випадково піти з екрану й залишити «невидимий» commit.

15. **Thermal print persistence**
    - `labelsPrinted` зберігається у background transaction відразу після успішного друку;
    - знищення Activity після фізичного друку не губить статус.

## Повторна статична валідація

- Java files: **197**
- Manifest Activity: **39**
- Activity classes missing from source: **0**
- Explicit Activity Intent targets checked: **18**, unregistered: **0**
- Explicit `Ui.button/Ui.secondaryButton` listener issues: **0**
- XML files parsed: **9**, malformed: **0**
- Java syntax-like errors from parser/compiler pass: **0**
- `ProjectIdGuard` host test: **PASS** (`../x`, `a/b`, `a\\b`, `id.json`, empty ID rejected)

## Що цим середовищем не можна підтвердити фізично

Немає Android runtime/emulator + реальної камери/ARCore/Bluetooth/мікрофона, тому hardware-path не можна чесно назвати фізично прокліканим. Код, Manifest, lifecycle, permissions, persistence та переходи перевірені статично; фінальний device smoke-test все одно потрібен на реальному Android після збірки APK.
