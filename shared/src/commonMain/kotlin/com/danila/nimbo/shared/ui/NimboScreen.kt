package com.danila.nimbo.shared.ui

enum class NimboScreen(
    val wireName: String,
    val title: String,
    val shortTitle: String,
    val glyph: String
) {
    HOME("home", "Главная", "Главная", "ϟ"),
    PROFILES("profiles", "Профили", "Профили", "◉"),
    ROUTING("routing", "Маршруты", "Маршруты", "⇄"),
    SETTINGS("settings", "Настройки", "Настройки", "⚙");

    companion object {
        fun fromWireName(value: String): NimboScreen = entries
            .firstOrNull { it.wireName == value.lowercase() }
            ?: HOME
    }
}
