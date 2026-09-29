package com.danila.nimbo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMotionPolicyTest {
    @Test
    fun `countdown rounds partial seconds up`() {
        assertEquals(2, SyncMotionPolicy.secondsLeft(nowMs = 1_000L, expiresAtMs = 2_001L))
    }

    @Test
    fun `expired countdown stops at zero`() {
        assertEquals(0, SyncMotionPolicy.secondsLeft(nowMs = 3_000L, expiresAtMs = 2_000L))
    }

    @Test
    fun `progress is clamped to session bounds`() {
        assertEquals(0f, SyncMotionPolicy.progress(2_000L, 2_000L, 1_000L), 0f)
        assertEquals(1f, SyncMotionPolicy.progress(500L, 2_000L, 1_000L), 0f)
        assertEquals(0.5f, SyncMotionPolicy.progress(1_500L, 2_000L, 1_000L), 0.001f)
    }
}
