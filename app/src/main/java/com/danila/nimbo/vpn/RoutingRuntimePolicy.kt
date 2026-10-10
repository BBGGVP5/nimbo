package com.danila.nimbo.vpn

/** Runtime requirements shared by generated and imported Xray configurations. */
object RoutingRuntimePolicy {
    /** Domain rules depend on HTTP/TLS/QUIC destination detection. */
    fun shouldEnableSniffing(userEnabled: Boolean, routingEnabled: Boolean, adBlockingEnabled: Boolean = false): Boolean =
        userEnabled || routingEnabled || adBlockingEnabled

    /** Saved routing changes must rebuild an already running native core and TUN. */
    fun shouldReloadTunnel(state: VpnState): Boolean = state != VpnState.DISCONNECTED
}
