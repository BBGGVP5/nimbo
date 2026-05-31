<p align="center">
  <img src="./nimbo.png" width="96" alt="Nimbo logo" />
</p>

<h1 align="center">Nimbo</h1>

<p align="center">
  Fast, lightweight VPN client for xray subscriptions on Windows and Android.
</p>

<p align="center">
  <strong>English</strong> · <a href="./README.md">Русский</a>
</p>

<p align="center">
  <img alt="Tauri 2" src="https://img.shields.io/badge/Tauri-2-24c8db?style=for-the-badge&logo=tauri&logoColor=white" />
  <img alt="React 19" src="https://img.shields.io/badge/React-19-61dafb?style=for-the-badge&logo=react&logoColor=111827" />
  <img alt="Rust" src="https://img.shields.io/badge/Rust-native-f97316?style=for-the-badge&logo=rust&logoColor=white" />
  <img alt="Windows" src="https://img.shields.io/badge/Windows-10%2F11-0078d4?style=for-the-badge&logo=windows11&logoColor=white" />
  <img alt="Android" src="https://img.shields.io/badge/Android-supported-3ddc84?style=for-the-badge&logo=android&logoColor=white" />
  <img alt="Open Source" src="https://img.shields.io/badge/Open%20Source-open-8b5cf6?style=for-the-badge&logo=github&logoColor=white" />
</p>

<p align="center">
  <a href="#-what-is-nimbo">Overview</a> ·
  <a href="#-features">Features</a> ·
  <a href="#-download">Download</a> ·
  <a href="#-open-source">Open Source</a> ·
  <a href="#-architecture">Architecture</a>
</p>

<p align="center">
  <img src="./nimbo-poster.png" alt="Nimbo poster" />
</p>

---

## ✨ What Is Nimbo

Nimbo is a clean VPN client for xray-compatible subscriptions on Windows and Android. It works with Remnawave, Marzban, 3x-ui, and any panel that returns standard `vless://`, `vmess://`, `trojan://`, `ss://`, or `hysteria2://` links.

The app imports a subscription URL, shows available servers, measures latency, generates the xray runtime config, and routes traffic through system proxy, TUN mode, or both.

---

## 💎 Why Nimbo

| Focus | Details |
|---|---|
| **Small footprint** | Tauri 2 uses the system WebView instead of bundling Chromium. |
| **Open source** | Transparent codebase: inspect it, audit it, build it yourself, and improve it through PRs. |
| **Native core** | Rust backend, xray-core integration, and a helper service for privileged network operations. |
| **One-time elevation** | The helper service is installed once, so connecting does not require repeated UAC prompts. |
| **Provider-friendly** | Subscription metadata, server descriptions, User-Agent presets, and `nimbo://` deep links. |
| **Practical routing** | TUN, proxy, combined mode, split tunneling, and custom routing profiles. |

---

## 🔓 Open Source

Nimbo is developed as an open-source project. You can inspect the code, audit it, build it yourself, and adapt it to your own workflows.

- **Transparency**: connection logic, helper-service behavior, xray config generation, and network operations are visible.
- **Security through audit**: users and providers can verify how the client handles subscriptions, DNS, routes, and local settings.
- **Build control**: build the client from source, review dependencies, and pin the version you want to run.
- **Community contribution**: bug reports, pull requests, and ideas around routing, UI, protocols, and platforms help the project move faster.

---

## 🚀 Features

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
- Automatic refresh by interval
- User-Agent presets for Happ/Incy compatibility
- Deep link import: `nimbo://import?url=...`
- Remnawave metadata support, including server descriptions

### Network Tools

- TCP latency checks for all servers
- Active connection monitor
- Session and total traffic statistics
- DNS leak protection
- LAN access controls
- Per-app split tunneling rules
- Custom routing profiles: domains, IPs, `geosite`, `geoip`

### Interface

- Russian and English languages
- Dark, Light, System, and True Black/OLED themes
- Provider accent color or custom accent color
- System tray quick actions
- Tunnel log viewer
- Compact responsive layout for desktop and mobile screens

---

## 🖥 Platforms

| Platform | Status |
|---|---|
| **Windows 10/11** | Main platform. NSIS/custom installer, helper service, and TUN support. |
| **Linux** | Experimental AppImage/deb/custom installer flow. |
| **Android** | Supported mobile client. |

---

## 📦 Download

Download the latest release from [Releases](../../releases).

> The installer is not code-signed yet. Windows SmartScreen may show a warning.
> Click **More info** → **Run anyway** if you trust the release.

---

## 🧭 Architecture

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

The UI runs as a regular user. The helper service handles privileged operations: TUN adapter setup, route changes, DNS configuration, xray runtime control, and cleanup of conflicting VPN processes.

---

## 📁 Project Structure

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
│   └── xray-config/    # xray JSON config generation
├── CHANGELOG_NIMBO.md
├── Cargo.toml
└── README.md
```

---

## 🔌 Subscription Compatibility

Nimbo works with any panel that provides xray-compatible subscriptions:

- **Remnawave**: metadata, server descriptions, and xray templates
- **Marzban**: base64 subscription links
- **3x-ui**: xray JSON and base64 formats
- **Generic panels**: standard proxy URLs

Default User-Agent:

```text
Nimbo/<version>
```

For legacy subscriptions, use the Happ/Incy User-Agent presets in settings.

---

## 🗺 Roadmap

- Android UX polish
- QR-code subscription import
- Platform kill switch hardening
- More split tunneling presets
- Multi-profile workflows
- Signed installer and auto-update hardening

---

## 🤝 Contributing

1. Open an issue to discuss the change.
2. Create a feature branch.
3. Make focused commits.
4. Run checks before opening a pull request.
5. Open a PR with a clear description and screenshots for UI changes.

---

## 📄 License

Nimbo is open source. Usage, modification, and redistribution terms are defined by the repository license.
