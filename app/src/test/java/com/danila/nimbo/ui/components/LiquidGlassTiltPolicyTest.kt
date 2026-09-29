package com.danila.nimbo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassTiltPolicyTest {

    @Test
    fun `portrait gravity maps to screen coordinates`() {
        val tilt = LiquidGlassTiltPolicy.fromGravity(
            gravityX = 3f,
            gravityY = -1.5f,
            displayRotation = LiquidGlassTiltPolicy.ROTATION_0
        )

        assertEquals(0.5f, tilt.x, 0.001f)
        assertEquals(0.25f, tilt.y, 0.001f)
    }

    @Test
    fun `landscape rotation remaps gravity axes`() {
        val tilt = LiquidGlassTiltPolicy.fromGravity(
            gravityX = 3f,
            gravityY = -1.5f,
            displayRotation = LiquidGlassTiltPolicy.ROTATION_90
        )

        assertEquals(0.25f, tilt.x, 0.001f)
        assertEquals(-0.5f, tilt.y, 0.001f)
    }

    @Test
    fun `extreme tilt is clamped`() {
        val tilt = LiquidGlassTiltPolicy.fromGravity(
            gravityX = 30f,
            gravityY = -30f,
            displayRotation = LiquidGlassTiltPolicy.ROTATION_0
        )

        assertEquals(1f, tilt.x, 0.001f)
        assertEquals(1f, tilt.y, 0.001f)
    }
}
