package com.danila.nimbo.ui.screens

internal object SyncMotionPolicy {
    fun secondsLeft(nowMs: Long, expiresAtMs: Long): Int =
        (((expiresAtMs - nowMs).coerceAtLeast(0L) + 999L) / 1_000L).toInt()

    fun progress(nowMs: Long, expiresAtMs: Long, lifetimeMs: Long): Float =
        ((expiresAtMs - nowMs).toFloat() / lifetimeMs.coerceAtLeast(1L))
            .coerceIn(0f, 1f)
}
