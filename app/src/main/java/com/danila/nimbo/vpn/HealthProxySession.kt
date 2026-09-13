package com.danila.nimbo.vpn

import java.util.UUID

/** Identity is per core start, not per host:port. Never log or persist credentials. */
internal class HealthProxySession(val ownerKey: String) {
    val username: String = "nimbo"
    val password: String = UUID.randomUUID().toString()
    internal var routeVerified: Boolean = false
}

internal object HealthProxySessions {
    @Volatile var active: HealthProxySession? = null
        private set

    fun activate(session: HealthProxySession?, verifiedRoute: Boolean = false) {
        session?.routeVerified = verifiedRoute
        active = session
    }
    fun invalidate() { active = null }

    fun connected(): HealthProxySession? = active?.takeIf {
        it.routeVerified && VpnManager.state.value == VpnState.CONNECTED &&
            VpnManager.connectedServer.value?.pingMeasurementKey() == it.ownerKey
    }

    fun isConnected(session: HealthProxySession): Boolean = connected() === session
}
