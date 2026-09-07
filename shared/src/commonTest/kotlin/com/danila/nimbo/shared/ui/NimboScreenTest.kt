package com.danila.nimbo.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NimboScreenTest {
    @Test
    fun parsesStableWireNamesAndFallsBackToHome() {
        assertEquals(NimboScreen.PROFILES, NimboScreen.fromWireName("profiles"))
        assertEquals(NimboScreen.SETTINGS, NimboScreen.fromWireName("SETTINGS"))
        assertEquals(NimboScreen.HOME, NimboScreen.fromWireName("unknown"))
    }

    @Test
    fun tabBarKeepsFourDestinations() {
        // Маршрутизация — экран настроек, а не вкладка: место в панели дорогое.
        assertEquals(
            listOf("home", "profiles", "stats", "settings"),
            NimboScreen.entries.filter { it.inTabBar }.map { it.wireName }
        )
        assertEquals(NimboScreen.ROUTING, NimboScreen.fromWireName("routing"))
        assertTrue(NimboScreen.entries.all { it.title.isNotBlank() && it.glyph.isNotBlank() })
    }
}
