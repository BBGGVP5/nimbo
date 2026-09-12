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

    /**
     * Subscription refreshes are intentionally paused while the tunnel is active.
     * Besides avoiding noisy notifications, this prevents a profile/server list
     * replacement from racing with the configuration currently used by the VPN.
     */
    fun canRefreshSubscriptions(
        autoUpdateEnabled: Boolean,
        vpnConnectionDesired: Boolean,
        vpnStateActive: Boolean
    ): Boolean = autoUpdateEnabled && !vpnConnectionDesired && !vpnStateActive

    fun shouldShowSystemNotification(
        notificationsEnabled: Boolean,
        appInForeground: Boolean,
        changedSubscriptions: Int,
        vpnActive: Boolean = false
    ): Boolean = notificationsEnabled && !appInForeground && changedSubscriptions > 0 && !vpnActive
}
