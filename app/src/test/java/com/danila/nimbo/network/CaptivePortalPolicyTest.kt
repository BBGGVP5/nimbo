package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptivePortalPolicyTest {
    @Test
    fun cellularPortalFlagDoesNotPauseRequestedVpn() {
        val decision = CaptivePortalPolicy.evaluate(
            CaptivePortalPolicy.State(),
            NetworkContextSnapshot(
                transport = NetworkTransport.CELLULAR,
                captivePortal = true,
                validated = false
            ),
            vpnRequested = true
        )
        assertEquals(CaptivePortalPolicy.Action.NONE, decision.action)
    }

    @Test
    fun cellularHandoffReleasesPreviousWifiPortalPauseWithoutValidation() {
        val decision = CaptivePortalPolicy.evaluate(
            CaptivePortalPolicy.State(portalWasDetected = true, tunnelPausedForPortal = true),
            NetworkContextSnapshot(
                transport = NetworkTransport.CELLULAR,
                captivePortal = false,
                validated = false
            ),
            vpnRequested = true
        )
        assertEquals(CaptivePortalPolicy.Action.RECOVER_TUNNEL, decision.action)
        assertEquals(CaptivePortalPolicy.State(), decision.state)
    }

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
