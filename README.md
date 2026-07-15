<p align="center">
  <img src="./nimbo.png" width="96" alt="Логотип Nimbo" />
</p>

<h1 align="center">Nimbo</h1>

<p align="center">
  Лёгкий VPN-клиент: подписки, серверы и маршруты — в одном понятном приложении.
</p>

<p align="center">
  <img alt="Tauri 2" src="https://img.shields.io/badge/Tauri-2-24c8db?style=for-the-badge&logo=tauri&logoColor=white" />
  <img alt="Rust" src="https://img.shields.io/badge/Rust-native-f97316?style=for-the-badge&logo=rust&logoColor=white" />
  <img alt="Android" src="https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3ddc84?style=for-the-badge&logo=android&logoColor=white" />
  <img alt="Windows and Linux" src="https://img.shields.io/badge/Windows%20%2B%20Linux-supported-0078d4?style=for-the-badge" />
</p>

<p align="center">
  <a href="https://nimboapp.pw">Официальный сайт</a> ·
  <a href="https://github.com/BBGGVP5/nimbo/releases/latest">Скачать последнюю версию</a> ·
  <a href="#о-приложении">О приложении</a> ·
  <a href="#возможности">Возможности</a> ·
  <a href="#платформы">Платформы</a> ·
  <a href="#официальные-каналы">Контакты</a>
</p>

<p align="center">
  <img src="./nimbo-poster-ru.png" alt="Постер Nimbo" />
</p>

## О приложении

Nimbo объединяет VPN-подписки, серверы и правила маршрутизации в одном интерфейсе. Добавьте ссылку от провайдера, выберите сервер — и подключайтесь через TUN, системный прокси или оба режима без ручной настройки конфигов.

Приложение работает с подписками Remnawave, Marzban, 3x-ui и другими панелями, которые выдают `vless://`, `vmess://`, `trojan://`, `ss://` или `hysteria2://`.

За новостями, документацией и актуальными релизами — на [nimboapp.pw](https://nimboapp.pw).

## Возможности

- импорт подписки по ссылке или deep link: серверы, лимит трафика и срок действия считываются автоматически;
- VLESS (Reality, XHTTP, WebSocket, gRPC), VMess, Trojan, Shadowsocks и Hysteria2;
- TUN, системный прокси и комбинированный режим;
- выбор серверов, проверка задержки, пользовательские маршруты и split tunneling;
- русская и английская локализация desktop-интерфейса;
- QR-импорт, уведомления и быстрая панель на Android.

## Платформы

| Платформа | Состояние | Технологии |
|---|---|---|
| Windows 10/11 | основной desktop-клиент | Tauri 2, React, Rust, служба и TUN |
| Linux x64 / arm64 | экспериментальная сборка | Tauri 2, React, Rust, AppImage/deb/RPM |
| Android 10+ | поддерживается | Kotlin, Jetpack Compose, `VpnService`, libXray |

Desktop-клиент автоматически получает актуальный Xray-core для Windows и Linux; Android использует libXray. Пользователь может указать собственный путь к ядру через `NIMBO_XRAY_PATH`.

## Структура исходников

```text
nimbo/
├── apps/
│   ├── android/       # Android-клиент
│   ├── ui/            # desktop-интерфейс Tauri + React
│   ├── service/       # служба Windows
│   └── installer/     # установщики Windows/Linux
├── crates/            # общие Rust-библиотеки desktop-клиента
├── docs/              # архитектура и инструкции выпуска
└── tools/             # воспроизводимые служебные скрипты
```

Подробности — в [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md).

## Официальные каналы

- Сайт: [nimboapp.pw](https://nimboapp.pw)
- Исходный код и релизы: [BBGGVP5/nimbo](https://github.com/BBGGVP5/nimbo)

История desktop-исходников и исходное авторство сохранены.
