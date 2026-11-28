# ElektorykScan v0.22.2 — Keyframe Backpressure Hardening

## Реально змінено
- `ScanVisualKeyframeRecorder`: додано backpressure для RGB keyframe writer. У пам'яті одночасно тримається не більше 3 незаписаних RGB кадрів; коли накопичувач/CPU не встигає стискати JPEG, фото-keyframe тимчасово пропускається, але Depth/AR сканування і semantic sampling продовжуються.
- Закрито lifecycle race між `maybeCapture()` та `close()/closeAsync()`: `RejectedExecutionException` більше не залишає metadata на JPEG/depth-файл, який ніколи не був записаний.
- Bitmap тепер гарантовано recycle-иться навіть при помилці створення/запису, а pending counter гарантовано зменшується у `finally`.
- `SemanticModelManager`: provisioning більше не може залишитися у `downloading=true`, якщо executor уже закритий; після `close()` нові завдання не приймаються.
- Версія: `0.22.2-keyframe-backpressure-hardening`, versionCode 63.

## Навіщо
До цього single-thread JPEG executor мав необмежену чергу. Кожен queued keyframe утримував `int[]` RGB (~до 640px по довгій стороні), тому на повільному накопичувачі/CPU довгий прохід міг створити десятки мегабайт transient heap, GC паузи та в крайньому разі OOM. Ліміт 3 переносить деградацію з "накопичувати RAM" у безпечне "пропустити зайвий texture keyframe"; метрична Depth-геометрія не залежить від цього фото-бюджету.

## Перевірка
- Java source inventory: 221 файлів.
- Статична перевірка дужок/структури після patch: PASS.
- ZIP integrity: `unzip -t` PASS.
- Повний Android assemble у цьому середовищі не може бути підтверджений без доступного Gradle 8.2/Android toolchain; AIDE compile залишається acceptance-кроком на пристрої.
