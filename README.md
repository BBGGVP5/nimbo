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

Nimbo импортирует подписки с `vless://`, `vmess://`, `trojan://`, `ss://`, `hysteria2://`, `naive+https://` и `naive+quic://`, показывает серверы, измеряет задержку и создаёт конфигурацию подключения. Для NaiveProxy Nimbo запускает официальный нативный клиент как локальный SOCKS-компонент. Интерфейс написан на React, desktop-оболочка и системная логика — на Tauri/Rust.

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
| Android | Планируется | Исходники будут в `apps/android/`; клиента пока нет |

AppImage подходит большинству дистрибутивов. DEB предназначен для Ubuntu, Debian, Linux Mint и Pop!_OS; RPM — для Fedora, RHEL-подобных систем и openSUSE.

## Структура исходников

```text
nimbo/
├── apps/
│   ├── ui/             # Tauri 2 + React desktop-клиент
│   ├── service/        # Windows-служба для привилегированных операций
│   ├── installer/      # Кастомные установщики Windows/Linux
│   └── android/        # Зарезервировано для будущего Android-клиента
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

## Автоматические релизы

Push в `main` и pull request запускают тесты и frontend-сборки на Windows и Linux. Push тега формата `v*` собирает Windows NSIS setup и Linux AppImage/DEB/RPM, после чего прикладывает файлы к GitHub Release.

```powershell
git tag v1.0.1
git push origin v1.0.1
```

Версия тега должна совпадать с версией в `apps/ui/src-tauri/tauri.conf.json` и workspace `Cargo.toml`.
Теги с суффиксом `-alpha`, `-beta` или `-rc` автоматически публикуются как prerelease и доступны пользователям канала «Бета»; обычные SemVer-теги попадают в «Стабильный» и «Бета».

## Безопасность и обновления

- Проверка обновлений приложения обращается к списку GitHub Releases проекта `BBGGVP5/nimbo`: стабильный канал исключает prerelease, бета-канал получает и предварительные сборки.
- Перед запуском установщика Nimbo обязательно сверяет SHA-256 файла с полем `digest` GitHub Release API. Если digest отсутствует или не совпадает, установка блокируется.
- После проверенной загрузки Nimbo сохраняет отпечаток asset (`id`, размер, `updated_at`, digest). Поэтому `gh release upload <tag> <file> --clobber` повторно уведомит уже обновившихся пользователей даже при неизменной версии; такое уведомление помечается как повторный выпуск с исправлениями и показывает release notes/commit.
- Windows-установщик сохраняет предыдущие `Nimbo.exe` и `nimbo-svc.exe`, проверяет новую сборку через `--update-health-check` и автоматически восстанавливает `.old`, если TUN, helper или новая сборка не прошли проверку.
- Xray загружается только при отсутствии локального runtime; перед распаковкой Nimbo сверяет SHA-256 с `.dgst` из официального выпуска XTLS/Xray-core.
- В production включена Content Security Policy Tauri; удалённые скрипты и произвольные origins не разрешены.

## Лицензия

Проект распространяется как проприетарный. Все права защищены, если отдельный файл лицензии не говорит иное.
