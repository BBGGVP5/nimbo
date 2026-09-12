package com.danila.nimbo.vpn

import android.content.Context

/** Applies persisted routing changes to the currently active VPN session. */
object RoutingConfigurationApplier {
    fun applyToActiveTunnel(context: Context): Boolean {
        if (!RoutingRuntimePolicy.shouldReloadTunnel(VpnManager.state.value)) return false
        return MyVpnService.requestConfigurationReload(context)
    }
}
