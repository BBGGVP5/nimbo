package com.danila.nimbo.utils

enum class VpnPillState { CONNECTING, CONNECTED, WAITING_NETWORK, RECOVERING, PAUSED, ATTENTION }

internal enum class VpnPillAvailability {
    UNSUPPORTED, APP_DISABLED, NOTIFICATIONS_BLOCKED, CHANNEL_BLOCKED,
    CHANNEL_MINIMIZED, SYSTEM_DISABLED, AVAILABLE, UNKNOWN
}

/** Fixed public vocabulary only: never expose a location, subscription or URL in the chip. */
internal fun vpnPillText(state: VpnPillState, seconds: Int, english: Boolean): String? = when (state) {
    VpnPillState.CONNECTING -> if (english) "Connect" else "Подкл."
    VpnPillState.CONNECTED -> if (seconds < 6) "NIMBO" else null
    VpnPillState.WAITING_NETWORK -> if (english) "Network" else "Сеть"
    VpnPillState.RECOVERING -> if (english) "Retry" else "Повтор"
    VpnPillState.PAUSED -> if (english) "Paused" else "Пауза"
    VpnPillState.ATTENTION -> "!"
}

/** Android channel constants: NONE=0, MIN=1, LOW=2. LOW is eligible, not a failure. */
internal fun vpnPillAvailability(
    supported: Boolean, enabled: Boolean, notificationsAllowed: Boolean,
    channelImportance: Int, promotionAllowed: Boolean?
): VpnPillAvailability = when {
    !supported -> VpnPillAvailability.UNSUPPORTED
    !enabled -> VpnPillAvailability.APP_DISABLED
    !notificationsAllowed -> VpnPillAvailability.NOTIFICATIONS_BLOCKED
    channelImportance == 0 -> VpnPillAvailability.CHANNEL_BLOCKED
    channelImportance == 1 -> VpnPillAvailability.CHANNEL_MINIMIZED
    promotionAllowed == false -> VpnPillAvailability.SYSTEM_DISABLED
    promotionAllowed == null -> VpnPillAvailability.UNKNOWN
    else -> VpnPillAvailability.AVAILABLE
}

/** The system owns the status chip's size and animation. Null means icon-only.
 * No timer or second notification is needed: the foreground service refreshes
 * its existing notification with the authoritative connection duration. */
internal fun vpnLiveUpdateText(connected: Boolean, seconds: Int, recovering: Boolean): String? = when {
    recovering || !connected -> "…"
    seconds < 6 -> "NIMBO"
    else -> null
}
