package com.danila.nimbo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAppIconManagerTest {
    @Test
    fun customShortcutUsesStableIdentifier() {
        assertEquals("nimbo_custom_icon", CustomAppIconManager.CUSTOM_SHORTCUT_ID)
    }

    @Test
    fun constructorKeepsAtLeastSixPresets() {
        assertTrue(CustomAppIconManager.presets.size >= 6)
    }
}
