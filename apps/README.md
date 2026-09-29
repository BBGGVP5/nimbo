# Приложения Nimbo

В `apps/` лежат исполняемые части проекта. Общая логика, не зависящая от платформы, остаётся в `crates/`.

| Папка | Назначение | Платформа |
|---|---|---|
| `ui/` | Основной интерфейс: React frontend и Rust/Tauri backend | Windows, Linux |
| `service/` | Служба с повышенными правами для сетевых операций | Windows |
| `installer/` | Отдельная оболочка установщика и скрипты упаковки | Windows, Linux |
| `../app/` | Актуальный Android-клиент (Gradle-проект в корне репозитория) | Android |

Общий Compose Multiplatform-код Android, iOS и desktop находится в `../shared/`. Для Android нужен собранный отдельно `app/libs/libxray.aar`; бинарный файл не хранится в Git.
