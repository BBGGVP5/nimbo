package com.danila.nimbo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class AccentResolutionTest {
    private val cobalt = Color(0xFF7298EE)
    private val lagoon = Color(0xFF55B8B0)

    @Test fun legacySignalStyleCannotReplaceAnySavedPresetWithOrange() {
        (0..17).forEach { index ->
            assertEquals(resolveUniversalAccent(index), getNebulaColors(
                themeIndex = index, elementStyle = ElementStyleMode.SIGNAL.persistedValue
            ).accent)
        }
        assertNotEquals(SignalEmber, getNebulaColors(8,
            elementStyle = ElementStyleMode.SIGNAL.persistedValue).accent)
    }

    @Test fun savedSingleAccentWinsOverEveryLegacyGradientAndBrightnessSetting() {
        listOf(cobalt, lagoon, Color(0xFFC77E67)).forEach { chosen ->
            listOf(1, 2, 3).forEach { count ->
                listOf(1f, 0.25f, 1.5f).forEach { brightness ->
                    val colors = getNebulaColors(themeIndex = 4, isCustomAccent = true,
                        customAccentColor = chosen, customGradientColor1 = Color.Red,
                        customGradientColor2 = Color.Yellow, customGradientColor3 = Color.Green,
                        customGradientCount = count, gradientEffectsEnabled = true,
                        elementStyle = ElementStyleMode.SIGNAL.persistedValue, globalBrightness = brightness)
                    assertEquals(chosen, colors.accent)
                    assertEquals(chosen, colors.statusConnecting)
                }
            }
        }
    }

    @Test fun providerIsReversibleExplicitOverlayOverPersonalAndDynamicChoices() {
        val spec = "signal, #C77E67, #FFFF00"
        val provider = Color(0xFFC77E67)
        assertEquals(provider, resolveUniversalAccent(8, true, cobalt, lagoon, true, spec))
        assertEquals(lagoon, resolveUniversalAccent(8, true, cobalt, lagoon, false, spec))
        assertEquals(cobalt, resolveUniversalAccent(8, true, cobalt, null, false, spec))
        assertEquals(cobalt, resolveUniversalAccent(8, true, cobalt, null, true, null))
        assertEquals(cobalt, resolveUniversalAccent(8, true, cobalt, null, true, "signal,#broken"))
    }

    @Test fun invalidProviderSpecCannotApplyAPartialGradientOrTransparentAccent() {
        listOf(null, "", "signal", "#xyz", "signal,#12345", "#oops,#7298EE").forEach {
            assertNull(subscriptionAccent(it))
        }
        assertEquals(cobalt, subscriptionAccent(" signal, #7298ee , #55B8B0"))
        assertEquals(cobalt, subscriptionAccent("#007298EE"))
    }

    @Test fun materialAndNimboControlsShareExactAccentWithoutTintingSurfaces() {
        listOf(true, false).forEach { dark ->
            val index = if (dark) 8 else 17
            listOf(cobalt, lagoon, Color(0xFFC77E67), Color(0xFF777777), Color.Black, Color.White).forEach { chosen ->
                val material = universalColorScheme(dark, chosen)
                val nimbo = getNebulaColors(index, isCustomAccent = true, customAccentColor = chosen)
                assertEquals(material.primary, nimbo.accent)
                assertEquals(material.surface, nimbo.surface)
                assertEquals(material.onSurface, nimbo.textPrimary)
                assertEquals(universalColorScheme(dark).surface, material.surface)
                val high = maxOf(material.primary.luminance(), material.onPrimary.luminance())
                val low = minOf(material.primary.luminance(), material.onPrimary.luminance())
                assertTrue((high + .05f) / (low + .05f) >= 4.5f)
            }
        }
    }

    @Test fun missingDynamicColorFallsBackToSavedChoiceAndColorsAreOpaque() {
        assertEquals(cobalt, resolveUniversalAccent(4, true, cobalt, dynamicAccent = null))
        assertEquals(cobalt, resolveUniversalAccent(4, true, cobalt.copy(alpha = .1f)))
        val dynamic = universalColorScheme(true, lagoon)
        assertEquals(lagoon, getNebulaColors(4, true, cobalt,
            useDynamicColor = true, colorScheme = dynamic).accent)
    }
}
