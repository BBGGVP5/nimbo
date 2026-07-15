<p align="center">
  <img src="./nimbo.png" width="96" alt="Логотип Nimbo" />
</p>

<h1 align="center">Nimbo</h1>

<p align="center">Лёгкий VPN-клиент для подписок, совместимых с Xray.</p>

<p align="center">
  <a href="https://github.com/BBGGVP5/nimbo/actions">GitHub Actions</a> ·
  <a href="../../releases">Релизы</a> ·
  <a href="./docs/build/linux.md">Сборка для Linux</a>
</p>

## О проекте

Nimbo импортирует подписки с `vless://`, `vmess://`, `trojan://`, `ss://` и `hysteria2://`, показывает серверы, измеряет задержку и создаёт конфигурацию Xray. Интерфейс написан на React, desktop-оболочка и системная логика — на Tauri/Rust.

Возможности текущей desktop-версии:

- системный proxy, TUN и комбинированный режим;
- маршрутизация по доменам, IP и приложениям;
- подписки Remnawave, Marzban, 3x-ui и совместимые форматы;
- русский и английский интерфейсы, темы, системный трей и журнал туннеля;
- автоматическая загрузка Xray из официального stable release с проверкой SHA-256.

## Платформы

| Платформа | Статус | Форматы релиза |
|---|---|---|
| Windows 10/11 x64 | Основная | NSIS setup (`.exe`) |
| Linux x64 | Экспериментальная | AppImage, DEB, RPM |
| Linux arm64 | Поддержан Xray runtime; пакет нужно собирать на arm64 Linux | AppImage/DEB/RPM при нативной сборке |
| Android 10+ | Экспериментальная мобильная версия | Kotlin/Compose, APK для arm64-v8a и armeabi-v7a |

AppImage подходит большинству дистрибутивов. DEB предназначен для Ubuntu, Debian, Linux Mint и Pop!_OS; RPM — для Fedora, RHEL-подобных систем и openSUSE.

## Структура исходников

```text
nimbo/
├── apps/
│   ├── ui/             # Tauri 2 + React desktop-клиент
│   ├── service/        # Windows-служба для привилегированных операций
│   ├── installer/      # Кастомные установщики Windows/Linux
│   └── android/        # Kotlin + Jetpack Compose Android-клиент
├── crates/
│   ├── device/         # Идентификация устройства
│   ├── ipc/            # Общие типы IPC
│   ├── subscription/   # Загрузка и разбор подписок
│   └── xray-config/    # Построение конфигурации Xray
├── docs/
│   └── build/          # Инструкции по сборке
└── .github/workflows/  # Проверки и публикация релизов
```

Подробности по границам модулей — в [apps/README.md](./apps/README.md).

## Требования для разработки

| Инструмент | Версия |
|---|---:|
| Rust | 1.80+ |
| Node.js | 22+ |
| npm | 10+ |
| Tauri CLI | ставится локально через `npm ci` |
| Android | JDK 21 и Android SDK; подробности в `apps/android/README.md` |

На Windows для запуска нужен WebView2 Runtime; в актуальных Windows 10/11 он обычно уже есть. Для кастомного Windows-установщика дополнительно нужен NSIS.

## Быстрый старт на Windows

```powershell
git clone https://github.com/BBGGVP5/nimbo.git
cd nimbo\apps\ui
npm ci
npm run dev
```

Сборка готового Windows-установщика с сервисом:

```powershell
winget install --id NSIS.NSIS -e
cd nimbo\apps\ui
npm ci
npm run build:installer:current
```

Готовый файл появится в `target\release\bundle\nsis\`.

## Сборка для Linux

Linux-пакеты собираются нативно в Linux. На компьютере с Windows используйте WSL2 с Ubuntu: это нормальная Linux-среда, а не кросс-сборка из PowerShell. Инструкция с командами и путями к файлам находится в [docs/build/linux.md](./docs/build/linux.md).

## Android

Android-клиент находится в [apps/android](./apps/android). Он использует `VpnService` и официальный `libXray` `26.7.11`; в нём уже есть Gradle-wrapper, unit-тесты, lint и профили arm64-v8a/armeabi-v7a. Для подписанного APK нужен личный keystore; инструкция находится в [apps/android/README.md](./apps/android/README.md).

## Автоматические релизы

Push в `main` и pull request запускают тесты, frontend-сборки и Android unit-тесты/lint. Push тега формата `v*` собирает Windows NSIS setup и Linux AppImage/DEB/RPM, после чего прикладывает файлы к GitHub Release. Android workflow собирает только неподписанный APK для проверки: публикация Android-релиза потребует настроить signing credentials.

```powershell
git tag v1.0.1
git push origin v1.0.1
```

Версия тега должна совпадать с версией в `apps/ui/src-tauri/tauri.conf.json` и workspace `Cargo.toml`.

## Безопасность и обновления

- Проверка обновлений приложения обращается к GitHub Release проекта `BBGGVP5/nimbo`.
- Desktop Xray загружается только при отсутствии локального runtime; перед распаковкой Nimbo сверяет SHA-256 с `.dgst` из официального выпуска XTLS/Xray-core.
- Android использует официальный `libXray`; скрипт `tools/update-libxray.ps1` проверяет SHA-256 GitHub release asset до замены AAR.
- В production включена Content Security Policy Tauri; удалённые скрипты и произвольные origins не разрешены.

## Лицензия

Проект распространяется как проприетарный. Все права защищены, если отдельный файл лицензии не говорит иное.
