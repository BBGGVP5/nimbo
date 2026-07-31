package com.danila.nimbo.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualStyleTest {
    @Test
    fun `stored zero resolves to liquid glass`() {
        assertEquals(ElementStyleMode.LIQUID_GLASS, ElementStyleMode.fromPersistedValue(0))
    }

    @Test
    fun `stored one resolves to material expressive`() {
        assertEquals(ElementStyleMode.MATERIAL_EXPRESSIVE, ElementStyleMode.fromPersistedValue(1))
    }

    @Test
    fun `unknown stored style falls back to liquid glass`() {
        assertEquals(ElementStyleMode.LIQUID_GLASS, ElementStyleMode.fromPersistedValue(Int.MAX_VALUE))
    }
}
