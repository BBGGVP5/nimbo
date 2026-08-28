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
    fun exposesTheSameFourPrimaryDestinationsAsAndroid() {
        assertEquals(
            listOf("home", "profiles", "apps", "settings"),
            NimboScreen.entries.map { it.wireName }
        )
        assertTrue(NimboScreen.entries.all { it.title.isNotBlank() && it.glyph.isNotBlank() })
    }
}
