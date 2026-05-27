<p align="center">
  <h1 align="center">Nimbo</h1>
  <p align="center">
    Быстрый и легковесный VPN-клиент для xray-подписок
    <br />
    Fast, lightweight VPN client for xray-based subscriptions
    <br /><br />
    Tauri 2 &bull; React 19 &bull; Rust
  </p>
</p>

<p align="center">
  <a href="#-возможности--features">Возможности</a> &bull;
  <a href="#-скачать--download">Скачать</a> &bull;
  <a href="#%EF%B8%8F-сборка--building-from-source">Сборка</a> &bull;
  <a href="#-структура--project-structure">Структура</a> &bull;
  <a href="#-лицензия--license">Лицензия</a>
</p>

---

## Что такое Nimbo? / What is Nimbo?

<details open>
<summary>🇷🇺 Русский</summary>

Nimbo — десктопный VPN-клиент для подписок на базе xray (Remnawave, Marzban, 3x-ui и другие). Импортируйте URL подписки, выберите сервер, подключитесь — Nimbo сделает остальное.

- **Легковесный** — установщик ~12 МБ, использует системный WebView вместо Chromium
- **Нативная производительность** — бэкенд на Rust + ядро xray-core
- **Без повторных UAC** — сервис устанавливается один раз, работает в фоне
- **Split tunneling** — маршрутизация отдельных приложений через VPN или в обход

</details>

<details>
<summary>🇬🇧 English</summary>

Nimbo is a desktop VPN client that connects to xray-compatible subscription services (Remnawave, Marzban, 3x-ui, and others). Import your subscription URL, pick a server, and connect — Nimbo handles the rest.

- **Tiny footprint** — ~12 MB installer, uses system WebView instead of bundling Chromium
- **Native performance** — Rust backend + xray-core under the hood
- **No repeated UAC prompts** — helper service installs once, runs in background
- **Split tunneling** — route specific apps through VPN or bypass it

</details>

---

## Возможности / Features

### Протоколы / Protocols
- VLESS (Reality, XHTTP, WebSocket, gRPC, HTTP/2, TCP)
- VMess (WebSocket, gRPC, TCP, HTTP Upgrade)
- Trojan (TLS, WebSocket, gRPC)
- Shadowsocks
- Hysteria2

### Режимы подключения / Connection modes
- Системный прокси / System proxy (SOCKS5 / HTTP)
- TUN-режим / TUN mode (весь системный трафик / captures all system traffic)
- Комбинированный / Combined (proxy + TUN)

<details open>
<summary>🇷🇺 Подробнее</summary>

**Управление подписками**
- Автоимпорт по URL с отображением информации (трафик, срок действия)
- Автообновление по настраиваемому интервалу
- Маскировка User-Agent (пресеты Happ, Incy) для совместимости с разными панелями
- Deep link поддержка (`nimbo://import?url=...`)

**Сеть**
- Правила проксирования по приложениям (split tunneling)
- Пользовательские правила маршрутизации (домен, IP, geosite, geoip)
- Тест задержки (TCP ping ко всем серверам)
- Мониторинг активных соединений
- Статистика трафика (за сессию и суммарная)
- Защита от DNS-утечек
- Настройки доступа к локальной сети

**Интерфейс**
- 4 темы: Системная, Тёмная, True Black (OLED), Светлая
- Кастомизация акцентных цветов от провайдера
- Русский и английский языки
- Системный трей с быстрым подключением/отключением
- Просмотр логов туннеля
- Описания серверов и брендинг от подписки

</details>

<details>
<summary>🇬🇧 All features</summary>

**Subscription management**
- Auto-import from URL with subscription info display (traffic, expiry)
- Auto-refresh on configurable interval
- User-Agent masking presets (Happ, Incy) for compatibility with various panels
- Deep link support (`nimbo://import?url=...`)

**Networking**
- Per-application proxy rules (split tunneling)
- Custom routing rules (domain, IP, geosite, geoip)
- Latency testing (TCP ping to all servers)
- Active connection monitoring
- Traffic statistics (upload/download per session and total)
- DNS leak protection
- LAN access settings

**Interface**
- 4 themes: System, Dark, True Black (OLED), Light
- Provider-customizable accent colors
- Russian and English languages
- System tray with quick connect/disconnect
- Tunnel logs viewer
- Subscription-provided server descriptions and branding

</details>

### Платформы / Platforms
- **Windows 10/11** — основная платформа (MSI/NSIS установщик)
- **Linux** — AppImage, .deb (экспериментально)

---

## Скачать / Download

### Windows
Скачайте последний `.exe` установщик со страницы [Releases](../../releases).

Download the latest `.exe` installer from the [Releases](../../releases) page.

> **Примечание / Note:** Установщик пока не подписан. Windows SmartScreen может показать предупреждение — нажмите «Подробнее» → «Выполнить в любом случае».
>
> The installer is not code-signed yet. SmartScreen may show a warning — click "More info" → "Run anyway".

### Linux
Скачайте `.AppImage` или `.deb` со страницы [Releases](../../releases).

---

## Сборка / Building from source

### Зависимости / Prerequisites

| Инструмент / Tool | Версия / Version | Назначение / Purpose |
|---|---|---|
| [Rust](https://rustup.rs/) | 1.80+ | Бэкенд и крейты / Backend and crates |
| [Node.js](https://nodejs.org/) | 20+ | Сборка фронтенда / Frontend build |
| [Tauri CLI](https://v2.tauri.app/start/prerequisites/) | 2.x | Сборщик приложения / App bundler |

**Windows:** WebView2 runtime (предустановлен в Windows 10 1803+ и Windows 11).

**Linux:**
```bash
sudo apt install libwebkit2gtk-4.1-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev build-essential patchelf
```

### Сборка / Build

```bash
# Клонировать / Clone
git clone https://github.com/BBGGVP5/nimbo.git
cd nimbo

# Установить зависимости фронтенда / Install frontend dependencies
cd apps/ui
npm install

# Режим разработки (hot-reload) / Development mode
npx tauri dev

# Продакшн-сборка (создаёт установщик) / Production build
npx tauri build
```

Собранный установщик будет в `target/release/bundle/`.

### Сборка сервиса / Build the helper service (Windows)

```bash
cargo build --release -p nimbo-svc
```

---

## Структура / Project structure

```
nimbo/
├── apps/
│   ├── ui/                     # Основное приложение / Main app — Tauri 2 + React 19 + Tailwind v4
│   │   ├── src/                # React-фронтенд / React frontend (pages, store, i18n)
│   │   └── src-tauri/          # Tauri Rust бэкенд / Tauri Rust backend (commands, state, tray)
│   ├── service/                # Вспомогательный сервис / Helper service (Rust, SYSTEM on Windows)
│   └── installer/              # Кастомный установщик / Custom installer (Tauri-based)
├── crates/
│   ├── device/                 # Генерация HWID / Hardware ID generation
│   ├── ipc/                    # IPC-протокол UI ↔ сервис / IPC protocol
│   ├── subscription/           # Парсер подписок / Subscription fetcher & parser
│   └── xray-config/            # Билдер конфигов xray / xray JSON config builder
├── Cargo.toml                  # Корень workspace / Workspace root
└── README.md
```

### Крейты / Crates

| Крейт / Crate | Описание / Description |
|---|---|
| `nimbo-subscription` | Загрузка подписок, определение формата (base64, xray JSON, список ссылок), парсинг протоколов в типизированные структуры `Server` |
| `nimbo-xray-config` | Генерация JSON-конфигов xray-core из распарсенных серверов — inbounds, outbounds, routing, DNS |
| `nimbo-ipc` | Общие типы IPC-сообщений для обмена между UI и сервисом через named pipe |
| `nimbo-device` | Кроссплатформенная генерация аппаратного отпечатка (HWID) |

### Архитектура / Architecture

```
┌─ Nimbo UI (user-mode) ──────────────┐
│  Tauri 2 + React + WebView2         │
│  Окно, трей, настройки               │
└──────────┬───────────────────────────┘
           │ named pipe (JSON)
┌──────────▼───────────────────────────┐
│  Nimbo Service (SYSTEM)              │
│  • управление xray-core              │
│  • TUN-адаптер (wintun)              │
│  • таблица маршрутизации             │
│  • защита от DNS-утечек              │
└──────────────────────────────────────┘
```

UI запускается от обычного пользователя. Сервис работает с повышенными привилегиями и управляет операциями, требующими прав администратора (TUN-адаптер, таблица маршрутизации, завершение конфликтующих VPN-процессов). UAC запрашивается **один раз** при установке.

The UI runs as a regular user. The helper service runs with elevated privileges and handles operations that require admin access. UAC is shown **once** during installation, not on every connect.

---

## Совместимость / Subscription compatibility

<details open>
<summary>🇷🇺 Русский</summary>

Nimbo работает с любым сервисом, предоставляющим xray-совместимые подписки:

- **Remnawave** — полная поддержка включая описания серверов и метаданные
- **Marzban** — base64-кодированные ссылки подписок
- **3x-ui** — форматы xray JSON и base64
- **Любая панель**, отдающая стандартные ссылки `vless://`, `vmess://`, `trojan://`, `ss://`, `hysteria2://`

Клиент отправляет `Nimbo/<version>` в качестве User-Agent по умолчанию. Если панель ожидает другой UA — используйте пресеты маскировки в настройках.

</details>

<details>
<summary>🇬🇧 English</summary>

Nimbo works with any service that provides xray-compatible subscriptions:

- **Remnawave** — full support including server descriptions and metadata
- **Marzban** — base64-encoded subscription links
- **3x-ui** — xray JSON and base64 formats
- **Any panel** that outputs standard `vless://`, `vmess://`, `trojan://`, `ss://`, or `hysteria2://` links

The client sends `Nimbo/<version>` as User-Agent by default. If your panel expects a specific UA, use the masking presets in Settings.

</details>

---

## Участие в разработке / Contributing

<details open>
<summary>🇷🇺 Русский</summary>

Мы рады вкладу в проект! Пожалуйста, сначала создайте Issue для обсуждения предлагаемых изменений.

1. Сделайте форк репозитория
2. Создайте ветку (`git checkout -b feature/my-feature`)
3. Зафиксируйте изменения
4. Отправьте ветку (`git push origin feature/my-feature`)
5. Откройте Pull Request

</details>

<details>
<summary>🇬🇧 English</summary>

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

</details>

---

## Лицензия / License

Проект распространяется под лицензией **GNU Affero General Public License v3.0** — см. файл [LICENSE](LICENSE).

This project is licensed under the **GNU Affero General Public License v3.0** — see the [LICENSE](LICENSE) file for details.
