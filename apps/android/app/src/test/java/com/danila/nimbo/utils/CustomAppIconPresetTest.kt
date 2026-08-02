package com.danila.nimbo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAppIconPresetTest {
    @Test
    fun `gallery exposes at least six unique presets`() {
        val presets = CustomAppIconManager.presets

        assertTrue(presets.size >= 6)
        assertEquals(presets.size, presets.map { it.config }.distinct().size)
    }
}
