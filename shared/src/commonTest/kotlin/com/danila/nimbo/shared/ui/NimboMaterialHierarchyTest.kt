package com.danila.nimbo.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NimboMaterialHierarchyTest {
    @Test fun floatingChromeIsStrongerThanContent() {
        val control = LiquidGlassMaterialPolicy.chromeStrength(LiquidGlassDepth.CONTROL)
        val panel = LiquidGlassMaterialPolicy.chromeStrength(LiquidGlassDepth.PANEL)
        val floating = LiquidGlassMaterialPolicy.chromeStrength(LiquidGlassDepth.FLOATING)
        assertTrue(control < panel && panel < floating)
        assertTrue(control > 0f && floating <= 1f)
    }

    @Test fun disabledEffectsRemoveTheSheen() {
        LiquidGlassDepth.entries.forEach { depth ->
            assertEquals(0f, LiquidGlassMaterialPolicy.sheenAlpha(depth, true, 0f))
            assertEquals(0f, LiquidGlassMaterialPolicy.sheenAlpha(depth, false, 0f))
        }
    }

    @Test fun quieterChromeDoesNotReduceOverlayOpacity() {
        for (dark in listOf(false, true)) {
            assertTrue(LiquidGlassMaterialPolicy.baseAlpha(LiquidGlassDepth.FLOATING, dark, true, 0f) >= 0.9f)
            assertTrue(LiquidGlassMaterialPolicy.sheenAlpha(LiquidGlassDepth.PANEL, dark, 1f) < 0.1f)
        }
    }
}
