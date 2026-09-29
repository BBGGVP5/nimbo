# Архитектура исходников Nimbo

Репозиторий разделён по продуктам и общим библиотекам. Это позволяет развивать Android и desktop независимо, не смешивая Gradle-артефакты с Rust и Node.js.

```text
nimbo/
├── app/              # Android: Kotlin, Compose, VpnService
├── shared/           # Общие модели и Compose Multiplatform UI
├── iosApp/           # iOS-приложение, виджет и Packet Tunnel
├── apps/
│   ├── ui/            # Tauri 2 + React: окно приложения и трей
│   ├── service/       # Windows-служба для привилегированных операций
│   └── installer/     # Кастомный установщик Windows/Linux
├── crates/
│   ├── device/        # HWID и информация об ОС
│   ├── ipc/           # Контракты IPC между UI и службой
│   ├── subscription/  # Загрузка и разбор подписок
│   ├── xray-config/   # Генератор конфигурации Xray
│   └── mihomo/        # Desktop-интеграция Mihomo
├── docs/              # Архитектура и релизные инструкции
└── tools/             # Воспроизводимые служебные скрипты
```

## Платформы

| Платформа | Реализация | Состояние |
|---|---|---|
| Windows 10/11 | Tauri, React, Rust-служба, TUN | основной desktop-клиент |
| Linux x64 | Tauri, React, Rust | экспериментальная сборка AppImage/deb |
| Android 10+ | Kotlin, Compose, `VpnService`, libXray | поддерживается |
| iOS/iPadOS | SwiftUI, Compose Multiplatform, Packet Tunnel | переподписываемая IPA; Mihomo TUN пока недоступен |

Desktop-клиент при первом запуске получает официальный актуальный Xray-core для Windows x64 или Linux x64, если бинарник не был положен рядом с приложением и не задан `NIMBO_XRAY_PATH`. Android использует проверенный `app/libs/libxray.aar`, не включённый в Git из-за размера; скрипт `tools/update-libxray.ps1` загружает его с проверкой SHA-256.

Общие Rust-crates остаются внутри desktop-контура, пока у Android и desktop нет стабильного общего API. Переносить туда Kotlin- или UI-код не нужно: это создаст ложную «унификацию» и усложнит релизы.
