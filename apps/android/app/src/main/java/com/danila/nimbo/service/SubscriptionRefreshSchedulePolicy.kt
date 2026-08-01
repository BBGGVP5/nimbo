package com.danila.nimbo.service

object SubscriptionRefreshSchedulePolicy {
    private const val MIN_DELAY_SECONDS = 5 * 60

    fun delaySeconds(configuredSeconds: Int): Long =
        configuredSeconds.coerceAtLeast(MIN_DELAY_SECONDS).toLong()

    fun isDue(
        nowMs: Long,
        lastSuccessMs: Long,
        configuredSeconds: Int
    ): Boolean {
        if (lastSuccessMs <= 0L) return true
        val elapsedMs = (nowMs - lastSuccessMs).coerceAtLeast(0L)
        return elapsedMs >= delaySeconds(configuredSeconds) * 1_000L
    }

    fun shouldShowSystemNotification(
        notificationsEnabled: Boolean,
        appInForeground: Boolean,
        changedSubscriptions: Int
    ): Boolean = notificationsEnabled && !appInForeground && changedSubscriptions > 0
}
