# Выпуск Nimbo

## Обязательная проверка

Перед публикацией должны пройти проверки обеих поддерживаемых реализаций:

```powershell
# Android
cd apps/android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease

# Desktop: Rust-часть
cd ..\..
cargo test --workspace

# Desktop: React/TypeScript
cd apps\ui
npm ci
npm run build
```

## Android

1. Обновите libXray через `tools/update-libxray.ps1`, если вышел стабильный релиз.
2. Проверьте номер версии в `apps/android/app/build.gradle.kts`.
3. Создайте локальный `apps/android/app/signing.properties` по примеру и соберите release APK.
4. Проверьте APK на реальном ARM64-устройстве: импорт подписки, VPN-подключение, переподключение и отключение.

Никогда не публикуйте APK, подписанный debug-ключом. Неподписанные APK из GitHub Actions — только артефакты проверки.

## Desktop

1. Проверьте номер версии workspace в `Cargo.toml`, UI в `apps/ui/package.json` и Tauri-конфигурацию.
2. Запустите Linux workflow либо локальную Linux-сборку, если выпуск включает Linux.
3. На Windows проверьте установку, запуск службы, TUN и загрузку Xray-core.
4. Создайте GitHub Release в `BBGGVP5/nimbo` только с проверенными установщиками и ясным списком изменений.

## Обновления зависимостей

Dependabot раз в неделю создаёт отдельные PR для Cargo, npm, Gradle и GitHub Actions. Их нужно проверять сборкой соответствующей платформы, а не объединять массовым обновлением без теста.
