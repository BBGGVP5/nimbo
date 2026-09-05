package com.danila.nimbo.shared.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NimboAppearanceTest {
    @Test fun boundsAndNonFiniteValuesAreSafe() {
        val value = NimboAppearance(brightness = Float.NaN, transparency = 9f, corners = -1f, textScale = Float.POSITIVE_INFINITY).normalized()
        assertEquals(1f, value.brightness)
        assertEquals(1f, value.transparency)
        assertEquals(0.25f, value.corners)
        assertEquals(1f, value.textScale)
    }

    @Test fun invalidPreferencesRecoverDefaults() {
        val value = NimboAppearance(themeMode = "invalid", accentHex = "garbage").normalized()
        assertEquals("system", value.themeMode)
        assertEquals("75A7FF", value.accentHex)
        assertEquals("FF0011", NimboAppearance(accentHex = "#ff0011").normalized().accentHex)
    }

    @Test fun themeFollowsSystemOnlyInSystemMode() {
        assertTrue(NimboAppearance().isDark(true))
        assertFalse(NimboAppearance().isDark(false))
        assertTrue(NimboAppearance(themeMode = "dark").isDark(false))
        assertFalse(NimboAppearance(themeMode = "light").isDark(true))
    }

    @Test fun oledBackgroundIsBlackAndPaperIsOpaque() {
        val colors = nimboColors(NimboAppearance(themeMode = "oled"), false)
        assertEquals(Color.Black, colors.background)
        assertEquals(Color.Black, colors.backgroundDeep)
        assertEquals(1f, colors.paper.alpha)
    }

    @Test fun lightThemeHasDarkInkAndCustomAccent() {
        val colors = nimboColors(NimboAppearance(themeMode = "light", accentHex = "#E63329"), true)
        assertEquals(Color(0xFFE63329), colors.accent)
        assertTrue(colors.text.red < colors.background.red)
        assertTrue(colors.ink.red < colors.paper.red)
    }

    @Test fun panelBrightnessDoesNotChangeAccentOrBackground() {
        val normal = nimboColors(NimboAppearance(), true)
        val bright = nimboColors(NimboAppearance(brightness = 2f), true)
        assertEquals(normal.background, bright.background)
        assertEquals(normal.accent, bright.accent)
        assertTrue(bright.surface.red > normal.surface.red)
    }
}
