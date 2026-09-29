package com.danila.nimbo.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarScrollPolicyTest {
    @Test
    fun `scrolling down hides bottom bar`() {
        assertFalse(BottomBarScrollPolicy.visibleAfterScroll(true, -12f))
    }

    @Test
    fun `scrolling up reveals bottom bar`() {
        assertTrue(BottomBarScrollPolicy.visibleAfterScroll(false, 12f))
    }

    @Test
    fun `tiny motion preserves current state`() {
        assertTrue(BottomBarScrollPolicy.visibleAfterScroll(true, -1f))
        assertFalse(BottomBarScrollPolicy.visibleAfterScroll(false, 1f))
    }

    @Test
    fun `tracker ignores finger jitter until direction is clear`() {
        val tracker = BottomBarScrollTracker(activationDistancePx = 20f)

        assertTrue(tracker.visibleAfterScroll(true, -7f))
        assertTrue(tracker.visibleAfterScroll(true, 4f))
        assertTrue(tracker.visibleAfterScroll(true, -8f))
        assertTrue(tracker.visibleAfterScroll(true, -8f))
        assertFalse(tracker.visibleAfterScroll(true, -8f))
    }

    @Test
    fun `tracker reveals only after accumulated upward scroll`() {
        val tracker = BottomBarScrollTracker(activationDistancePx = 20f)

        assertFalse(tracker.visibleAfterScroll(false, 9f))
        assertFalse(tracker.visibleAfterScroll(false, 9f))
        assertTrue(tracker.visibleAfterScroll(false, 3f))
    }
}
