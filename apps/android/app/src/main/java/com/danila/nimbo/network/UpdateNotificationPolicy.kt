package com.danila.nimbo.network

import com.danila.nimbo.model.UpdateKind

/** Pure policy that prevents an undeliverable notification from being consumed. */
internal object UpdateNotificationPolicy {
    const val REPAIR_REMINDER_INTERVAL_MS = 12L * 60L * 60L * 1000L

    fun canPost(
        permissionGranted: Boolean,
        appNotificationsEnabled: Boolean,
        channelEnabled: Boolean
    ): Boolean = permissionGranted && appNotificationsEnabled && channelEnabled

    fun shouldPost(
        identity: String,
        lastIdentity: String?,
        kind: UpdateKind,
        lastNotifiedAt: Long,
        now: Long
    ): Boolean {
        if (identity != lastIdentity) return true
        if (kind != UpdateKind.REPAIR) return false
        val elapsed = (now - lastNotifiedAt).coerceAtLeast(0L)
        return elapsed >= REPAIR_REMINDER_INTERVAL_MS
    }
}
