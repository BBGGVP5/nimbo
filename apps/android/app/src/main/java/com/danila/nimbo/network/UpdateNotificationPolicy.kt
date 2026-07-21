package com.danila.nimbo.network

/** Pure policy that prevents an undeliverable notification from being consumed. */
internal object UpdateNotificationPolicy {
    fun canPost(
        permissionGranted: Boolean,
        appNotificationsEnabled: Boolean,
        channelEnabled: Boolean
    ): Boolean = permissionGranted && appNotificationsEnabled && channelEnabled
}
