# Nimbo для Android

Android-клиент Nimbo написан на Kotlin и Jetpack Compose. Он использует `VpnService` и официальный `libXray` для подключения к xray-совместимым подпискам.

## Что включено

- Android 10+ (`minSdk 29`), ARM64 и armeabi-v7a;
- libXray `26.7.11` с TUN-интерфейсом, geoip/geosite и защитой сокетов через `VpnService.protect`;
- R8 и сжатие ресурсов в release-варианте;
- unit-тесты и lint в GitHub Actions;
- безопасная release-конфигурация: debug-ключ никогда не используется для публикации.

## Локальная сборка

Нужны Android SDK, JDK 21 и доступ к Maven Central/Google Maven.

```powershell
cd apps/android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Без credentials release APK собирается неподписанным. Это правильно для CI и проверки исходников, но такой APK нельзя раздавать пользователям.

## Подпись релиза

Скопируйте пример и укажите путь к своему keystore:

```powershell
Copy-Item app/signing.properties.example app/signing.properties
.\gradlew.bat :app:assembleRelease
```

`signing.properties` исключён из Git. Альтернатива для CI — передать значения как Gradle properties: `nimboReleaseStoreFile`, `nimboReleaseStorePassword`, `nimboReleaseKeyAlias`, `nimboReleaseKeyPassword`.

## Обновление ядра

Android AAR обновляется только из официальных релизов XTLS/libXray:

```powershell
.\tools\update-libxray.ps1
cd apps/android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Скрипт проверяет наличие `libgojni.so` для ARM64 и armeabi-v7a, прежде чем заменить `app/libs/libxray.aar`.
