package com.danila.nimbo.shared.ui

enum class NimboScreen(
    val wireName: String,
    val title: String,
    val shortTitle: String,
    val glyph: String
) {
    HOME("home", "Главная", "Главная", "ϟ"),
    PROFILES("profiles", "Профили", "Профили", "◉"),
    STATS("stats", "Статистика", "Статистика", "▤"),
    SETTINGS("settings", "Настройки", "Настройки", "⚙"),

    /**
     * Маршрутизация живёт в настройках, а не в нижней панели: настройка редкая,
     * а место в панели дорогое. [inTabBar] отделяет вкладки от таких экранов.
     */
    ROUTING("routing", "Маршрутизация", "Маршруты", "⇄");

    val inTabBar: Boolean
        get() = this != ROUTING

    companion object {
        fun fromWireName(value: String): NimboScreen = entries
            .firstOrNull { it.wireName == value.lowercase() }
            ?: HOME
    }
}
