package com.danila.nimbo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassRimPolicyTest {

    @Test
    fun `complete perimeter keeps a visible base rim`() {
        val samples = LiquidGlassRimPolicy.samples(
            tiltX = 0f,
            tiltY = 0f,
            isDark = true,
            effectStrength = 1f,
            steps = 32
        )

        assertEquals(33, samples.size)
        assertTrue(samples.all { it.alpha > 0.04f })
    }

    @Test
    fun `tilt moves brightest sector around perimeter`() {
        val right = LiquidGlassRimPolicy.samples(1f, 0f, true, 1f, steps = 32)
            .dropLast(1)
        val down = LiquidGlassRimPolicy.samples(0f, 1f, true, 1f, steps = 32)
            .dropLast(1)

        val rightPeak = right.indices.maxBy { right[it].alpha }
        val downPeak = down.indices.maxBy { down[it].alpha }
        assertTrue(kotlin.math.abs(rightPeak - downPeak) >= 6)
    }

    @Test
    fun `tiny tilt cannot abruptly rotate the resting highlight`() {
        val resting = LiquidGlassRimPolicy.samples(0f, 0f, true, 1f, steps = 64)
            .dropLast(1)
        val tinyTilt = LiquidGlassRimPolicy.samples(0.05f, 0f, true, 1f, steps = 64)
            .dropLast(1)
        val restingPeak = resting.indices.maxBy { resting[it].alpha }
        val tiltedPeak = tinyTilt.indices.maxBy { tinyTilt[it].alpha }
        val directDistance = kotlin.math.abs(restingPeak - tiltedPeak)
        val circularDistance = minOf(directDistance, resting.size - directDistance)

        assertTrue("rest=$restingPeak tilt=$tiltedPeak", circularDistance <= 2)
    }

    @Test
    fun `sweep samples join continuously`() {
        val samples = LiquidGlassRimPolicy.samples(
            tiltX = -0.6f,
            tiltY = 0.4f,
            isDark = false,
            effectStrength = 0.7f,
            steps = 32
        )

        assertEquals(samples.first().alpha, samples.last().alpha, 0.001f)
        assertEquals(samples.first().whiteMix, samples.last().whiteMix, 0.001f)
        assertEquals(samples.first().spectrumMix, samples.last().spectrumMix, 0.001f)
    }
}
