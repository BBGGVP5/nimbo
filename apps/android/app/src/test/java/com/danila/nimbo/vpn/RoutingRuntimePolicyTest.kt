package com.danila.nimbo.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRuntimePolicyTest {
    @Test
    fun `routing forces sniffing even when the advanced toggle is off`() {
        assertTrue(RoutingRuntimePolicy.shouldEnableSniffing(userEnabled = false, routingEnabled = true))
        assertFalse(RoutingRuntimePolicy.shouldEnableSniffing(userEnabled = false, routingEnabled = false))
    }

    @Test
    fun `active tunnel states require a config reload`() {
        assertFalse(RoutingRuntimePolicy.shouldReloadTunnel(VpnState.DISCONNECTED))
        assertTrue(RoutingRuntimePolicy.shouldReloadTunnel(VpnState.CONNECTING))
        assertTrue(RoutingRuntimePolicy.shouldReloadTunnel(VpnState.CONNECTED))
    }
}
