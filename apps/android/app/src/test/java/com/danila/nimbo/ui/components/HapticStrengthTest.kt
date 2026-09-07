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
            val tick = HapticPatternPolicy.tick(strength, HapticStyle.Crisp)
            val confirmation = HapticPatternPolicy.confirmation(strength, HapticStyle.Crisp)

            assertTrue(confirmation.amplitudes.max() > tick.amplitudes.max())
            assertTrue(confirmation.timings.sum() > tick.timings.sum())
        }
    }

    @Test
    fun `persisted values map to stable haptic profiles`() {
        assertEquals(HapticStyle.Soft, HapticStyle.fromPersistedValue(0))
        assertEquals(HapticStyle.Crisp, HapticStyle.fromPersistedValue(1))
        assertEquals(HapticStyle.Double, HapticStyle.fromPersistedValue(2))
        assertEquals(HapticStyle.Wave, HapticStyle.fromPersistedValue(3))
        assertEquals(HapticStyle.Pulse, HapticStyle.fromPersistedValue(4))
        assertEquals(HapticStyle.Spring, HapticStyle.fromPersistedValue(5))
        assertEquals(HapticStyle.Crisp, HapticStyle.fromPersistedValue(99))
    }

    @Test
    fun `soft profile is gentler and double confirmation has two pulses`() {
        val soft = HapticPatternPolicy.tick(HapticStrength.Medium, HapticStyle.Soft)
        val crisp = HapticPatternPolicy.tick(HapticStrength.Medium, HapticStyle.Crisp)
        val double = HapticPatternPolicy.confirmation(HapticStrength.Medium, HapticStyle.Double)

        assertTrue(soft.amplitudes.max() < crisp.amplitudes.max())
        assertTrue(double.timings.size > 2)
        assertTrue(double.amplitudes.count { it > 0 } >= 2)
    }

    @Test
    fun `every selectable profile creates a valid perceptible waveform`() {
        HapticStyle.entries.forEach { style ->
            val pattern = HapticPatternPolicy.confirmation(HapticStrength.Medium, style)

            assertEquals(pattern.timings.size, pattern.amplitudes.size)
            assertTrue(pattern.timings.sum() > 0L)
            assertTrue(pattern.amplitudes.any { it > 0 })
        }
    }
}
