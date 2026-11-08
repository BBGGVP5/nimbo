plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    idea
}

// Монорепо: в корне лежат десктоп (Rust/Tauri), iOS, Go-ядра и десятки ГБ
// сборок и временных файлов. Android Studio индексировала всё это при каждом
// открытии. Исключаем всё, кроме Android-модулей (app, shared); на сборку
// Gradle это не влияет, только на индекс IDE.
idea {
    module {
        excludeDirs.addAll(
            listOf(
                ".build", ".build-dependencies", ".codex-tmp", ".supergoal",
                "apps", "artifacts", "assets", "crates", "docs", "iosApp",
                "release-upload", "scripts", "target", "tools",
            ).map(::file)
        )
    }
}
