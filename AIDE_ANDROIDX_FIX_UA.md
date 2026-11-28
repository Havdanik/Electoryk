# AIDE AndroidX fix — v0.16.4

Виправлено падіння `:app:checkDebugAarMetadata`:

- `android.useAndroidX=true`
- `android.enableJetifier=true`
- ARCore `com.google.ar:core:1.54.0` може коректно підтягувати `androidx.annotation`.
- Перевірено, що вихідний код проєкту не містить імпортів `android.support.*`, тому конфлікту зі старою Support Library немає.
- ZIP має плоский корінь: `app/`, `gradle/`, `gradlew`, `build.gradle`, `settings.gradle` лежать одразу після розпакування.
