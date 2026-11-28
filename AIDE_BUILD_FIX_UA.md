# AIDE BUILD FIX — v0.16.2

Причина помилки `./gradlew: No such file or directory` у v0.16.0: у ZIP не було launch-скрипта Gradle Wrapper.

У v0.16.2:
- `gradlew` є в корені проєкту і має executable bit;
- якщо AIDE вже має Gradle 8.2 — він використовується без повторного завантаження;
- якщо Gradle 8.2 немає — перший Build завантажить офіційний `gradle-8.2-bin.zip` з services.gradle.org;
- за наявності `sha256sum` перевіряється SHA-256 дистрибутива;
- build stack узгоджений: JDK 17 + AGP 8.2.2 + Gradle 8.2 + compileSdk/targetSdk 34;
- ARCore лишився підключений через Maven dependency; перша збірка потребує інтернету, якщо dependency ще не в кеші.

## У AIDE
1. Видали/закрий старий імпорт v0.16.0.
2. Розпакуй v0.16.2 окремою папкою.
3. Відкрий саме папку `ElektorykScan_AIDE_v0.16.2_AIDE_BUILD_FIXED`.
4. Натисни Build.
5. Перший Build може довше вантажити Gradle/AGP/ARCore; наступні мають використовувати кеш.
