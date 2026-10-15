package com.danila.nimbo.utils

import org.junit.Assert.*
import org.junit.Test

class ServerSwitchPolicyTest {
    @Test fun `live selection is enabled by default but an explicit opt out is respected`() {
        assertTrue(ServerSwitchPolicy.DEFAULT_ENABLED)
        assertTrue(ServerSwitchPolicy.shouldSwitch(true, ServerSwitchPolicy.DEFAULT_ENABLED, true))
        assertFalse(ServerSwitchPolicy.shouldSwitch(true, false, true))
    }
    @Test fun `same server and idle VPN do not initiate a connection`() {
        assertFalse(ServerSwitchPolicy.shouldSwitch(true, true, false))
        assertFalse(ServerSwitchPolicy.shouldSwitch(false, true, true))
    }
}
