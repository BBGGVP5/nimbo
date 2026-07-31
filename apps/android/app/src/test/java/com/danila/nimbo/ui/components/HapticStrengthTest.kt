package com.danila.nimbo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticStrengthTest {

    @Test
    fun `persisted values map to stable strength levels`() {
        assertEquals(HapticStrength.Light, HapticStrength.fromPersistedValue(0))
        assertEquals(HapticStrength.Medium, HapticStrength.fromPersistedValue(1))
        assertEquals(HapticStrength.Strong, HapticStrength.fromPersistedValue(2))
    }

    @Test
    fun `unknown persisted strength falls back to medium`() {
        assertEquals(HapticStrength.Medium, HapticStrength.fromPersistedValue(-1))
        assertEquals(HapticStrength.Medium, HapticStrength.fromPersistedValue(99))
    }

    @Test
    fun `tick pulse grows with selected strength`() {
        val light = HapticPulsePolicy.tick(HapticStrength.Light)
        val medium = HapticPulsePolicy.tick(HapticStrength.Medium)
        val strong = HapticPulsePolicy.tick(HapticStrength.Strong)

        assertTrue(light.amplitude < medium.amplitude)
        assertTrue(medium.amplitude < strong.amplitude)
        assertTrue(light.durationMs < medium.durationMs)
        assertTrue(medium.durationMs < strong.durationMs)
    }

    @Test
    fun `confirmation pulse is stronger than tick`() {
        HapticStrength.entries.forEach { strength ->
            val tick = HapticPulsePolicy.tick(strength)
            val confirmation = HapticPulsePolicy.confirmation(strength)

            assertTrue(confirmation.amplitude > tick.amplitude)
            assertTrue(confirmation.durationMs > tick.durationMs)
        }
    }
}
