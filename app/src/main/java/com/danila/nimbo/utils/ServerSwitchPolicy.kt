package com.danila.nimbo.utils

/** Row selection reconnects only an existing VPN session; it never auto-starts idle VPN. */
object ServerSwitchPolicy {
    const val DEFAULT_ENABLED = true
    fun shouldSwitch(vpnActive: Boolean, enabled: Boolean, isDifferentServer: Boolean): Boolean =
        vpnActive && enabled && isDifferentServer
}
