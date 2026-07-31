package com.danila.nimbo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassMaterialPolicyTest {

    @Test
    fun `dark glass is calmer and darker than light glass`() {
        LiquidGlassDepth.entries.forEach { depth ->
            val dark = LiquidGlassMaterialPolicy.baseAlpha(
                depth = depth,
                isDark = true,
                reducedTransparency = false,
                panelAlpha = 0.42f
            )
            val light = LiquidGlassMaterialPolicy.baseAlpha(
                depth = depth,
                isDark = false,
                reducedTransparency = false,
                panelAlpha = 0.62f
            )

            assertTrue("$depth dark=$dark light=$light", dark < light)
        }
    }

    @Test
    fun `transparency slider changes actual glass alpha and effects`() {
        val defaultAlpha = LiquidGlassMaterialPolicy.baseAlpha(
            LiquidGlassDepth.PANEL,
            isDark = true,
            reducedTransparency = false,
            panelAlpha = 0.42f
        )
        val transparentAlpha = LiquidGlassMaterialPolicy.baseAlpha(
            LiquidGlassDepth.PANEL,
            isDark = true,
            reducedTransparency = false,
            panelAlpha = 0.105f
        )

        assertTrue(transparentAlpha < defaultAlpha * 0.3f)
        assertEquals(
            0.25f,
            LiquidGlassMaterialPolicy.effectStrength(
                isDark = true,
                reducedTransparency = false,
                panelAlpha = 0.105f
            ),
            0.001f
        )
    }

    @Test
    fun `reduced transparency uses opaque readable material`() {
        val alpha = LiquidGlassMaterialPolicy.baseAlpha(
            LiquidGlassDepth.CONTROL,
            isDark = true,
            reducedTransparency = true,
            panelAlpha = 0.42f
        )

        assertTrue(alpha >= 0.88f)
    }
}
