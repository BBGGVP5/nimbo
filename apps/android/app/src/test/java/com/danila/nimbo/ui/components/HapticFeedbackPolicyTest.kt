package com.danila.nimbo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticFeedbackPolicyTest {

    @Test
    fun `continuous slider stays quiet inside the same bucket`() {
        val first = HapticFeedbackPolicy.sliderBucket(
            value = 0.101f,
            rangeStart = 0f,
            rangeEnd = 1f,
            steps = 0
        )
        val second = HapticFeedbackPolicy.sliderBucket(
            value = 0.139f,
            rangeStart = 0f,
            rangeEnd = 1f,
            steps = 0
        )

        assertEquals(first, second)
        assertFalse(HapticFeedbackPolicy.shouldEmitSliderTick(first, second))
    }

    @Test
    fun `continuous slider emits after crossing a bucket`() {
        val first = HapticFeedbackPolicy.sliderBucket(0.14f, 0f, 1f, steps = 0)
        val second = HapticFeedbackPolicy.sliderBucket(0.16f, 0f, 1f, steps = 0)

        assertTrue(HapticFeedbackPolicy.shouldEmitSliderTick(first, second))
    }

    @Test
    fun `discrete slider uses material step intervals`() {
        assertEquals(0, HapticFeedbackPolicy.sliderBucket(0f, 0f, 10f, steps = 4))
        assertEquals(1, HapticFeedbackPolicy.sliderBucket(2f, 0f, 10f, steps = 4))
        assertEquals(5, HapticFeedbackPolicy.sliderBucket(10f, 0f, 10f, steps = 4))
    }

    @Test
    fun `slider values are clamped to their range`() {
        assertEquals(0, HapticFeedbackPolicy.sliderBucket(-10f, 0f, 1f, steps = 0))
        assertEquals(
            HapticFeedbackPolicy.CONTINUOUS_SLIDER_INTERVALS,
            HapticFeedbackPolicy.sliderBucket(10f, 0f, 1f, steps = 0)
        )
    }

    @Test
    fun `first observed slider value does not vibrate`() {
        assertFalse(HapticFeedbackPolicy.shouldEmitSliderTick(null, 4))
    }
}
