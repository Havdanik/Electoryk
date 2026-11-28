# ElektorykScan v0.17.1 — install compatibility fix

## Що перевірено у проблемному APK v0.17.0

- ZIP/APK структура ціла.
- Всі DEX мають коректні header checksum та SHA-1 signature.
- AndroidManifest.xml структурно коректний.
- package: `com.elektoryk.scan`.
- minSdk 26, targetSdk 34, versionCode 25.
- APK Signature Scheme v2 криптографічно валідний.
- Сертифікат збігається з v0.16.4 debug APK.
- Native ARCore ELF для arm64 має 16 KB ELF LOAD alignment, але самі uncompressed `.so` усередині APK були ZIP-aligned лише на 4 KB.

## Зміна v0.17.1

Проєкт використовує AGP 8.2.2. Для сумісності зі встановленням на нові Android 15/16 додано:

```gradle
packagingOptions {
    jniLibs {
        useLegacyPackaging true
    }
}
```

Це змушує пакувати native `.so` у legacy/compressed режимі, щоб Package Installer витягав їх на диск і не залежав від 16 KB ZIP alignment усередині APK. Логіка Smart Scan не змінювалась.

## Якщо після цього Samsung усе ще покаже «помилка аналізу пакета»

Тоді потрібен точний install reason з Package Installer/ADB (`INSTALL_PARSE_FAILED_*` або `INSTALL_FAILED_*`). Але найпідозріліша packaging-проблема у v0.17.0 цим проходом прибрана.
