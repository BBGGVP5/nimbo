# Выпуск Nimbo

## Обязательная проверка

Перед публикацией должны пройти проверки обеих поддерживаемых реализаций:

```powershell
# Android (с подготовленным app/libs/libxray.aar)
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease

# Desktop: Rust-часть
cargo test --workspace

# Desktop: React/TypeScript
cd apps\ui
npm ci
npm run build
```

## Android

1. Подготовьте `app/libs/libxray.aar` из проверенного официального выпуска. Этот большой бинарный файл не хранится в Git; `tools/update-libxray.ps1` проверяет SHA-256 при загрузке.
2. Проверьте номер версии в `app/build.gradle.kts`.
3. Создайте локальный `app/signing.properties` по примеру и соберите release APK.
4. Проверьте APK на реальном ARM64-устройстве: импорт подписки, VPN-подключение, переподключение и отключение.

Никогда не публикуйте APK, подписанный debug-ключом. Текущая GitHub-проверка Android не собирает APK без локального native AAR.

## iOS

Workflow `build-ios-unsigned.yml` собирает IPA на macOS runner. Артефакт переподписываемый, но не готов к установке без сертификата, provisioning profile и Network Extension entitlement. Mihomo TUN на iOS пока недоступен; успешная сборка не подтверждает работу этого ядра в туннеле.

## Desktop

1. Проверьте номер версии workspace в `Cargo.toml`, UI в `apps/ui/package.json` и Tauri-конфигурацию.
2. Запустите Linux workflow либо локальную Linux-сборку, если выпуск включает Linux.
3. На Windows проверьте установку, запуск службы, TUN и загрузку Xray-core.
4. Создайте GitHub Release в `BBGGVP5/nimbo` только с проверенными установщиками и ясным списком изменений.

## Обновления зависимостей

Dependabot раз в неделю создаёт отдельные PR для Cargo, npm, Gradle и GitHub Actions. Их нужно проверять сборкой соответствующей платформы, а не объединять массовым обновлением без теста.
