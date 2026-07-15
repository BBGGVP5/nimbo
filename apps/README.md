# Приложения Nimbo

В `apps/` лежат исполняемые части проекта. Общая логика, не зависящая от платформы, остаётся в `crates/`.

| Папка | Назначение | Платформа |
|---|---|---|
| `ui/` | Основной интерфейс: React frontend и Rust/Tauri backend | Windows, Linux |
| `service/` | Служба с повышенными правами для сетевых операций | Windows |
| `installer/` | Отдельная оболочка установщика и скрипты упаковки | Windows, Linux |
| `android/` | Kotlin/Jetpack Compose-клиент, `VpnService`, libXray и Gradle-проект | Android 10+ |

Не переносите desktop-файлы в `android/`: это самостоятельный Gradle-проект. Общие форматы подписок и конфигурации нужно согласованно развивать в `crates/subscription`, `crates/xray-config` и Android-модуле, чтобы клиенты сохраняли совместимость.
