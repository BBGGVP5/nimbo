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
}
