package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptivePortalPolicyTest {
    @Test
    fun portalPausesTunnelThenValidatedNetworkRecoversIt() {
        val detected = CaptivePortalPolicy.evaluate(
            CaptivePortalPolicy.State(),
            NetworkContextSnapshot(
                transport = NetworkTransport.WIFI,
                captivePortal = true,
                validated = false
            ),
            vpnRequested = true
        )
        assertEquals(CaptivePortalPolicy.Action.PAUSE_AND_SHOW_LOGIN, detected.action)

        val recovered = CaptivePortalPolicy.evaluate(
            detected.state,
            NetworkContextSnapshot(
                transport = NetworkTransport.WIFI,
                captivePortal = false,
                validated = true
            ),
            vpnRequested = true
        )
        assertEquals(CaptivePortalPolicy.Action.RECOVER_TUNNEL, recovered.action)
    }
}
