package com.danila.nimbo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.danila.nimbo.ui.components.contrastingLabel
import com.danila.nimbo.ui.components.usesConnectedCloud
import com.danila.nimbo.ui.components.NimboOperation
import com.danila.nimbo.ui.components.operationPhrases
import com.danila.nimbo.ui.components.OPERATION_PHRASE_INTERVAL_MS
import org.junit.Assert.*
import org.junit.Test

class UniversalThemeTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun bothModesKeepTextReadableOnCanvasPanelsAndButtons() {
        listOf(true, false).forEach { dark ->
            val colors = universalColorScheme(dark)
            assertTrue(contrast(colors.onSurface, colors.surface) >= 7f)
            assertTrue(contrast(colors.onSurfaceVariant, colors.surface) >= 4.5f)
            assertTrue(contrast(colors.onSurfaceVariant, colors.background) >= 4.5f)
            assertTrue(contrast(colors.primary, contrastingLabel(colors.primary)) >= 7f)
            assertEquals(colors.surface.red, colors.surface.green, 0f)
            assertEquals(colors.surface.green, colors.surface.blue, 0f)
        }
    }

    @Test fun userAccentsReceiveTheMoreReadableButtonLabel() {
        listOf(Color(0xFF888888), Color(0xFF75A7FF), Color(0xFFAF2424), Color.Black, Color.White).forEach {
            assertTrue(contrast(it, contrastingLabel(it)) >= 4.5f)
        }
    }

    @Test fun defaultPaletteUsesUniversalIndexWithoutRenumberingSavedPalettes() {
        assertEquals(8, DEFAULT_COLOR_THEME_INDEX)
        assertEquals(Color(0xFF121212), universalColorScheme(true).background)
        assertEquals(Color(0xFFE8E8E8), universalColorScheme(true).primary)
        assertEquals(Color(0xFF202020), universalColorScheme(false).primary)
    }
    @Test fun cloudRequiresEstablishedConnectionWithoutAnyTransition() {
        listOf(false, true).forEach { connected ->
            listOf(false, true).forEach { connecting ->
                listOf(false, true).forEach { disconnecting ->
                    assertEquals(connected && !connecting && !disconnecting,
                        usesConnectedCloud(connected, connecting, disconnecting))
                }
            }
        }
    }
    @Test fun secondaryCopyHasLocalizedVariantsAndNoProgressValues() {
        assertEquals(4500L, OPERATION_PHRASE_INTERVAL_MS)
        assertTrue(operationPhrases(NimboOperation.Connection).size >= 9)
        NimboOperation.entries.forEach { operation ->
            val phrases = operationPhrases(operation)
            assertTrue(phrases.size >= 3)
            phrases.forEach { (ru, en) ->
                assertTrue(ru.isNotBlank() && en.isNotBlank())
                assertNotEquals(ru, en)
                assertFalse((ru + en).contains(Regex("[0-9%]")))
            }
        }
    }
}
