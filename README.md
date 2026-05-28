<p align="center">
  <img src="./nimbo.png" width="96" alt="Nimbo logo" />
</p>

<h1 align="center">Nimbo</h1>

<p align="center">
  Fast, lightweight VPN client for xray subscriptions.
  <br />
  Быстрый и легковесный VPN-клиент для xray-подписок.
</p>

<p align="center">
  <img alt="Tauri 2" src="https://img.shields.io/badge/Tauri-2-24c8db?style=for-the-badge&logo=tauri&logoColor=white" />
  <img alt="React 19" src="https://img.shields.io/badge/React-19-61dafb?style=for-the-badge&logo=react&logoColor=111827" />
  <img alt="Rust" src="https://img.shields.io/badge/Rust-native-f97316?style=for-the-badge&logo=rust&logoColor=white" />
  <img alt="Windows" src="https://img.shields.io/badge/Windows-10%2F11-0078d4?style=for-the-badge&logo=windows11&logoColor=white" />
  <img alt="Android" src="https://img.shields.io/badge/Android-supported-3ddc84?style=for-the-badge&logo=android&logoColor=white" />
</p>

<p align="center">
  <a href="#-what-is-nimbo--что-такое-nimbo">Overview</a> ·
  <a href="#-features--возможности">Features</a> ·
  <a href="#-download--скачать">Download</a> ·
  <a href="#-build-from-source--сборка">Build</a> ·
  <a href="#-architecture--архитектура">Architecture</a>
</p>

<p align="center">
  <img src="./nimbo-poster.png" alt="Nimbo poster" />
</p>

---

## ✨ What Is Nimbo / Что такое Nimbo

Nimbo is a clean VPN client for xray-compatible subscriptions on Windows and Android: Remnawave, Marzban, 3x-ui and any panel that returns standard `vless://`, `vmess://`, `trojan://`, `ss://` or `hysteria2://` links.

Nimbo imports a subscription URL, shows available servers, measures latency, builds the xray runtime config and connects through system proxy, TUN mode or both.

Nimbo — аккуратный VPN-клиент для xray-совместимых подписок на Windows и Android: Remnawave, Marzban, 3x-ui и любых панелей со стандартными ссылками `vless://`, `vmess://`, `trojan://`, `ss://`, `hysteria2://`.

---

## 💎 Why Nimbo / Почему Nimbo

| Focus | Details |
|---|---|
| **Small footprint** | Tauri 2 uses the system WebView instead of bundling Chromium. |
| **Native core** | Rust backend, xray-core integration and a helper service for privileged network operations. |
| **One-time elevation** | The helper service is installed once, so connecting does not require repeated UAC prompts. |
| **Provider-friendly** | Subscription metadata, server descriptions, User-Agent presets and `nimbo://` deep links. |
| **Practical routing** | TUN, proxy, combined mode, split tunneling and custom routing profiles. |

---

## 🚀 Features / Возможности

### Protocols

- VLESS: Reality, XHTTP, WebSocket, gRPC, HTTP/2, TCP
- VMess: WebSocket, gRPC, TCP, HTTP Upgrade
- Trojan: TLS, WebSocket, gRPC
- Shadowsocks
- Hysteria2

### Connection Modes

- System proxy: SOCKS5 / HTTP
- TUN mode: full-system traffic capture
- Combined mode: proxy + TUN

### Subscription Management

- URL import with traffic and expiration metadata
- Auto-refresh by interval
- User-Agent presets for Happ/Incy compatibility
- Deep link import: `nimbo://import?url=...`
- Remnawave metadata support, including server descriptions

### Network Tools

- TCP latency testing for all servers
- Active connection monitor
- Session and total traffic statistics
- DNS leak protection
- LAN access controls
- Per-app split tunneling rules
- Custom routing profiles: domain, IP, `geosite`, `geoip`

### Interface

- Russian and English languages
- Dark, Light, System and True Black/OLED themes
- Provider or custom accent color
- System tray quick actions
- Tunnel log viewer
- Compact responsive layout for desktop and mobile screens

---

## 🖥 Platforms / Платформы

| Platform | Status |
|---|---|
| **Windows 10/11** | Main target. NSIS/custom installer, helper service, TUN support. |
| **Linux** | Experimental AppImage/deb/custom installer flow. |
| **Android** | Supported mobile client experience. |

---

## 📦 Download / Скачать

Download the latest build from [Releases](../../releases).

Скачайте последнюю сборку на странице [Releases](../../releases).

> The installer is not code-signed yet. Windows SmartScreen may show a warning.
> Click **More info** → **Run anyway** if you trust the build.

> Установщик пока не подписан. Windows SmartScreen может показать предупреждение.
> Нажмите **Подробнее** → **Выполнить в любом случае**, если доверяете сборке.

---

## 🛠 Build From Source / Сборка

### Prerequisites

| Tool | Version | Purpose |
|---|---:|---|
| [Rust](https://rustup.rs/) | 1.80+ | Workspace crates and Tauri backend |
| [Node.js](https://nodejs.org/) | 20+ | React/Vite frontend |
| [Tauri CLI](https://v2.tauri.app/start/prerequisites/) | 2.x | Desktop app bundling |

Windows 10/11 already includes WebView2 on most modern installs. If not, install the WebView2 Runtime from Microsoft.

Linux packages:

```bash
sudo apt install libwebkit2gtk-4.1-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev build-essential patchelf
```

### Development

```bash
git clone https://github.com/BBGGVP5/nimbo.git
cd nimbo/apps/ui
npm install
npm run dev
```

### Desktop Build

```bash
cd apps/ui
npm run build
npm run build:installer
```

The generated installer is placed under `target/release/bundle/`.

### Helper Service

```bash
cargo build --release -p nimbo-svc
```

---

## 🧭 Architecture / Архитектура

```text
Nimbo UI
Tauri 2 + React + WebView2
Window, tray, settings, subscriptions
        |
        | named pipe / JSON IPC
        v
Nimbo Service
Rust, SYSTEM on Windows
xray-core, TUN adapter, routing table, DNS protection
```

The UI runs as a regular user. The helper service handles privileged operations: TUN adapter setup, route changes, DNS configuration, xray runtime control and conflicting VPN process cleanup.

UI запускается от обычного пользователя. Сервис работает с повышенными правами и берёт на себя TUN-адаптер, маршруты, DNS, xray runtime и очистку конфликтующих VPN-процессов.

---

## 📁 Project Structure / Структура

```text
nimbo/
├── apps/
│   ├── ui/             # Tauri 2 + React frontend and Tauri backend
│   ├── service/        # Rust helper service
│   └── installer/      # Custom installer
├── crates/
│   ├── device/         # HWID generation
│   ├── ipc/            # Shared IPC protocol types
│   ├── subscription/   # Subscription fetcher and parser
│   └── xray-config/    # xray JSON config builder
├── CHANGELOG_NIMBO.md
├── Cargo.toml
└── README.md
```

---

## 🔌 Subscription Compatibility / Совместимость

Nimbo works with any panel that provides xray-compatible subscriptions:

- **Remnawave**: metadata, descriptions and xray templates
- **Marzban**: base64 subscription links
- **3x-ui**: xray JSON and base64 formats
- **Generic panels**: standard proxy URLs

Default User-Agent:

```text
Nimbo/<version>
```

For legacy subscriptions, use the Happ/Incy User-Agent presets in settings.

---

## 🗺 Roadmap / Планы

- Android UX polish
- QR-code subscription import
- Platform kill switch hardening
- More split tunneling presets
- Multi-profile workflows
- Signed installer and auto-update hardening

---

## 🤝 Contributing / Участие

1. Open an issue to discuss the change.
2. Create a feature branch.
3. Make focused commits.
4. Run checks before opening a pull request.
5. Open a PR with a clear description and screenshots for UI changes.

---

## 📄 License / Лицензия

Proprietary. All rights reserved unless a separate license file states otherwise.

Проприетарный проект. Все права защищены, если отдельный файл лицензии не говорит иначе.
