# Nimbo

Windows VPN-клиент для Remnawave-подписок. Аналог Happ, ориентированный на персональное использование.

> Status: planning. Код ещё не написан. План ниже.

---

## Что делает

1. Импортирует подписку по URL (`https://sub.domain.com/...`).
2. Парсит конфиги из подписки (VLESS Reality, VLESS XHTTP, VMess, Trojan, Shadowsocks).
3. Поднимает локальный VPN-туннель через TUN-адаптер.
4. Маршрутизирует весь системный трафик через выбранный сервер.
5. Показывает список серверов с пингом, статусом, остатком трафика.
6. Автообновляет подписку.

## Стек и архитектура

### Технологии
- **UI**: Tauri 2 + React + TypeScript + Tailwind. Бинарь ~10-15 MB через WebView2.
- **Service**: Rust (`windows-service` крейт). Отдельный бинарь `nimbo-svc.exe`, работает как Windows Service от SYSTEM.
- **Ядро**: `xray-core` (`xray.exe`) — нативная совместимость с Remnawave subscription templates.
- **TUN**: `tun2socks.exe` (hev-socks5-tunnel) + `wintun.dll` (Cloudflare WireGuard build).
- **IPC**: named pipe `\\.\pipe\nimbo-svc` между UI и сервисом.
- **Установщик**: WiX MSI (через `tauri-bundler`).

### Почему такой стек
- Tauri вместо Electron — бинарь в 10× меньше, нативный WebView2 (Edge уже стоит в Win10/11).
- Rust для сервиса — один тулчейн на весь проект (Cargo workspace), `windows-service` хорошо ложится, async-IO через `tokio`.
- xray-core вместо sing-box — Remnawave subscription templates по умолчанию заточены под xray (особенно XHTTP-transport), работают без сюрпризов. Hysteria2/TUIC если когда-то понадобятся — отдельным процессом sing-box рядом.

### Service-mode архитектура
```
+- Nimbo UI (user-mode) -----------+
|  Tauri + React + WebView2        |
|  Окно, tray-иконка, настройки    |
+--------+-------------------------+
         | named pipe
         | команды: connect/disconnect/status/import-subscription
+--------v-------------------------+
|  Nimbo Service (SYSTEM)          |
|  - управляет xray.exe            |
|  - управляет tun2socks.exe       |
|  - поднимает wintun adapter      |
|  - правит таблицу маршрутизации  |
|  - DNS leak protection           |
+----------------------------------+
```

Зачем сервис: TUN-адаптер на винде требует SYSTEM-прав. Без сервиса каждый запуск UI будет дёргать UAC. С сервисом — UAC спрашивается **один раз** при установке, дальше UI запускается обычным юзером и шлёт команды сервису по pipe. Так работают WireGuard for Windows, OpenVPN GUI, NekoRay, Happ.

## Структура репо

```
nimbo-app/
├─ apps/
│  ├─ ui/                  # Tauri + React (Nimbo.exe)
│  │  ├─ src/              # React/TS
│  │  └─ src-tauri/        # Tauri Rust backend
│  └─ service/             # Rust Windows Service (nimbo-svc.exe)
├─ crates/
│  ├─ ipc/                 # общий протокол UI ↔ Service (serde)
│  ├─ subscription/        # парсер подписок
│  ├─ xray-config/         # билдер xray JSON
│  └─ core-runner/         # обёртка над xray.exe + tun2socks.exe
├─ resources/
│  ├─ xray/xray.exe
│  ├─ tun2socks/tun2socks.exe
│  └─ wintun/wintun.dll
├─ installer/
│  └─ wix/                 # WiX MSI definitions
├─ Cargo.toml              # workspace root
└─ README.md
```

---

## План MVP

### Этап 1 — Скелет workspace
- Создать Cargo workspace в корне (`Cargo.toml` с `[workspace]`)
- Поднять Tauri 2 проект в `apps/ui` (Vite + React + TS + Tailwind)
- Создать пустой `apps/service` бинарь
- Создать `crates/ipc` с базовыми serde-структурами команд
- Тёмная тема, базовая навигация (Home / Subscriptions / Settings)
- Smoke-test: `cargo build --workspace` и `pnpm tauri dev` запускаются

### Этап 2 — Парсер подписок (`crates/subscription`)
- HTTP-клиент (`reqwest`) с тайм-аутом 15 сек, custom user-agent
- Детектор формата ответа:
  - base64-encoded list (плейн → расшифровать → построчный список ссылок)
  - plain text список ссылок
  - xray JSON (распознавание по `outbounds` ключу)
  - sing-box JSON (по `outbounds[].type`)
  - Clash YAML (по `proxies:` ключу)
- Парсеры URL-схем:
  - `vless://uuid@host:port?type=xhttp&security=reality&pbk=...&fp=...&sni=...&sid=...&flow=...#name`
  - `vmess://base64({v,ps,add,port,id,aid,scy,net,type,host,path,tls,sni,...})`
  - `trojan://password@host:port?security=tls&sni=...&type=...#name`
  - `ss://method:password@host:port#name` + base64-варианты
- Парсинг заголовка `subscription-userinfo: upload=...; download=...; total=...; expire=...`
- Хранение подписки: `apps/service/data/subscriptions.json` (зашифровать AES-GCM ключом из DPAPI)
- Тесты на реальных образцах с Remnawave (положить в `crates/subscription/tests/fixtures/`)

### Этап 3 — Билдер xray-конфига (`crates/xray-config`)
- Скелет конфига: `log`, `dns` (1.1.1.1 + 8.8.8.8 через прокси), `inbounds` (SOCKS5 на 127.0.0.1:10808), `outbounds`, `routing`
- Конвертеры: `Server → xray Outbound` для каждого протокола
- Маршрутизация: `direct` для приватных сетей (10/8, 192.168/16, 172.16/12), `proxy` для остального
- Опционально: rules для CN/RU geosite (на потом)
- Валидация конфига через `xray.exe -test -c config.json`

### Этап 4 — Service skeleton (`apps/service`)
- `windows-service` крейт: install/uninstall команды (`nimbo-svc.exe install`)
- Регистрация автозапуска (StartType=AutoStart)
- Named pipe сервер `\\.\pipe\nimbo-svc`, JSON-протокол поверх
- Команды: `Ping`, `GetStatus`, `Connect(serverId)`, `Disconnect`, `ReloadConfig`, `Shutdown`
- Логирование в `%PROGRAMDATA%\Nimbo\logs\service.log` (ротация по 10 MB × 5 файлов)

### Этап 5 — Core runner (`crates/core-runner`)
- Запуск `xray.exe -c config.json` как child process
- Перехват stdout/stderr → лог-файл
- Health-check: HTTP GET `http://127.0.0.1:10809/stats` (xray API) каждые 5 сек
- Авторестарт при падении (с экспоненциальным backoff, max 3 попытки)
- Graceful shutdown: SIGTERM-эквивалент через `TerminateProcess` + cleanup

### Этап 6 — TUN-интеграция
- `tun2socks.exe -device wintun -proxy socks5://127.0.0.1:10808 -tcp-rcv-buf-size 4194304 -loglevel warning`
- Поднятие wintun adapter (имя `Nimbo`)
- Добавление маршрутов: `0.0.0.0/1` и `128.0.0.0/1` через TUN (вместо `0.0.0.0/0` чтобы не пересекаться с дефолтным)
- Сохранение оригинального default gateway для restore при отключении
- DNS: установить только на TUN (через `netsh interface ipv4 set dns Nimbo static 1.1.1.1`)
- При отключении: удалить маршруты, восстановить DNS, опустить wintun

### Этап 7 — UI (минимальный)
- Экран «Подписки»: добавить URL → парсинг → сохранение, список подписок
- Экран «Серверы»: список из активной подписки, ping-кнопка, переключатель активного
- Главный экран: статус подключения, кнопка Connect/Disconnect, текущий сервер, базовая статистика (uptime, скорость up/down)
- Tauri commands → IPC к сервису через named pipe

### Этап 8 — Ping-тестер
- Реальный TCP-ping (connect к host:port с тайм-аутом) или HTTP-ping через xray API
- Параллельный пинг до 10 серверов одновременно
- Сортировка списка по пингу (опция)
- Авто-выбор лучшего сервера (опция в настройках)

### Этап 9 — Tray + автозапуск UI
- Tauri tray plugin: иконка в трее, контекстное меню (Show/Hide/Connect/Disconnect/Quit)
- Закрытие крестиком → minimize-to-tray (опция)
- Автозапуск UI с виндой (registry `HKCU\...\Run` или Startup folder)
- Hotkeys: глобальные шорткаты на toggle (опция)

### Этап 10 — Авто-обновление подписки
- WorkManager-эквивалент в сервисе: `tokio::time::interval` на 6/12/24 часа (настраивается)
- При обновлении: тянем URL, парсим, диффим со старым списком, обновляем
- Уведомления в трее при существенных изменениях (новые серверы / удалённые)
- Force-update кнопка в UI

### Этап 11 — Установщик MSI (WiX)
- WiX 4 проект в `installer/wix/`
- Компоненты: `Nimbo.exe`, `nimbo-svc.exe`, `xray.exe`, `tun2socks.exe`, `wintun.dll`, ярлыки в Start Menu / Desktop
- При установке: регистрация и запуск сервиса (UAC-пром)
- При удалении: остановка сервиса, удаление, очистка `%PROGRAMDATA%\Nimbo\` (опционально через диалог)
- Подпись пока пропускаем (см. секцию Code-signing ниже)

### Этап 12 — Auto-update приложения
- Tauri updater plugin
- Сервер обновлений: GitHub Releases (приватный репо → API token в приложении или прокси через свой сервер)
- Проверка раз в сутки + кнопка «проверить сейчас»
- Дельта-обновления при возможности

---

## Подводные камни (известные заранее)

- **wintun.dll**: только Cloudflare-сборка (текущая 0.14.x), не GPL-форк. Кладётся рядом с `tun2socks.exe`.
- **DNS leak**: обязательно устанавливать DNS только на TUN-адаптер. Если оставить системный DNS на Ethernet — винда будет резолвить через провайдера.
- **Route metrics**: использовать `0.0.0.0/1` + `128.0.0.0/1` (две половины) вместо `0.0.0.0/0`. Иначе при дисконнекте не получится корректно восстановить дефолтный маршрут.
- **IPv6**: если у юзера IPv6 — будет утечка мимо TUN. Решения: (1) отключать IPv6 на интерфейсах при коннекте, (2) добавлять `::/1` + `8000::/1` маршруты на TUN.
- **Kill-switch**: при падении xray трафик пойдёт в обход. Реализуется через WFP-фильтры (Windows Filtering Platform). Опционально на пост-MVP.
- **SmartScreen**: без code-signing будет «Windows protected your PC» при первом запуске установщика. Юзеры обходят через `More info → Run anyway`.
- **Антивирусы**: Defender может ругаться на `wintun.dll` и `tun2socks.exe` — добавляем в исключения через установщик (опционально).
- **Code page**: вывод `xray.exe` на винде с русской локалью может приходить в CP866. Парсить с явным `WINDOWS-1251` / `UTF-8` детектом, не предполагать UTF-8.
- **xray gRPC API**: на 127.0.0.1:10809 без auth. Биндить только на loopback, иначе любой локальный процесс может стрить статистику и перенастраивать ядро.
- **Concurrent pipes**: named pipe сервер должен поддерживать несколько UI-инстансов (tray + main window). Использовать `tokio::net::windows::named_pipe::NamedPipeServer` с reconnect-loop.

---

## Релизный процесс (на будущее)

1. Версия в `Cargo.toml` (workspace) и `apps/ui/package.json` синхронно
2. Тесты: `cargo test --workspace` + `pnpm test` в `apps/ui`
3. Build release: `pnpm tauri build` (генерит MSI)
4. Тег без префикса `v`: `0.1.0`, `0.2.0`
5. GitHub Release (приватный) с MSI-артефактом
6. Auto-update подхватывает из releases.json

## Code-signing — отдельной задачей

- **Старт**: без подписи. SmartScreen-варнинг при первой установке, юзеры обходят `More info → Run anyway`.
- **Дальше**: Sectigo Standard Code Signing (~$200/год) на физика. HSM-токен или облачный HSM. В CI шаг `signtool sign /tr http://timestamp.sectigo.com /td sha256 /fd sha256 /a Nimbo.msi`. Репутация набирается 1-3 месяца.
- **EV (опционально)**: если регистрировать ИП/ООО под проект — мгновенная репутация, ~$300-500/год.

---

## TODO post-MVP

- iOS / Android клиенты (Tauri Mobile или нативные)
- Split-tunneling per-app (через WFP application filter)
- Kill-switch (WFP block-all-except-tun)
- Профили (несколько подписок одновременно с переключением)
- Subscription через QR-код
- Импорт ZIP-бэкапа конфигурации
- Темы / кастомизация UI
- Telemetry (opt-in, в свой ClickHouse)

---

## Лицензирование

Приватный проект. Все права защищены. Текущий LICENSE (AGPL-3.0) — placeholder из дефолта GitHub, подлежит замене на proprietary.
