package com.danila.nimbo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnLiveUpdateTest {
    @Test fun compactVocabularyFitsNativeChipInBothLanguages() {
        for (state in VpnPillState.entries) for (english in listOf(false, true)) {
            val text = vpnPillText(state, 0, english)
            assertEquals(true, text != null && text.length <= 7)
        }
    }
    @Test fun networkPauseAndBudgetDoNotClaimReconnecting() {
        assertEquals("Сеть", vpnPillText(VpnPillState.WAITING_NETWORK, 500, false))
        assertEquals("Повтор", vpnPillText(VpnPillState.RECOVERING, 500, false))
        assertEquals("Пауза", vpnPillText(VpnPillState.PAUSED, 500, false))
        assertEquals("!", vpnPillText(VpnPillState.ATTENTION, 500, false))
    }
    @Test fun localizedConnectionSettlesWithoutDurationText() {
        assertEquals("Подкл.", vpnPillText(VpnPillState.CONNECTING, 0, false))
        assertEquals("Connect", vpnPillText(VpnPillState.CONNECTING, 0, true))
        assertEquals("VPN", vpnPillText(VpnPillState.CONNECTED, -1, false))
        assertEquals("VPN", vpnPillText(VpnPillState.CONNECTED, 5, true))
        assertNull(vpnPillText(VpnPillState.CONNECTED, 6, false))
    }
    @Test fun eligibilityDoesNotMistakeLowChannelForMinimized() {
        assertEquals(VpnPillAvailability.AVAILABLE, vpnPillAvailability(true, true, true, 2, true))
        assertEquals(VpnPillAvailability.CHANNEL_MINIMIZED, vpnPillAvailability(true, true, true, 1, true))
        assertEquals(VpnPillAvailability.CHANNEL_BLOCKED, vpnPillAvailability(true, true, true, 0, true))
    }
    @Test fun permissionAndSystemRestrictionsRemainVisible() {
        assertEquals(VpnPillAvailability.UNSUPPORTED, vpnPillAvailability(false, true, true, 2, true))
        assertEquals(VpnPillAvailability.APP_DISABLED, vpnPillAvailability(true, false, true, 2, true))
        assertEquals(VpnPillAvailability.NOTIFICATIONS_BLOCKED, vpnPillAvailability(true, true, false, 2, true))
        assertEquals(VpnPillAvailability.SYSTEM_DISABLED, vpnPillAvailability(true, true, true, 2, false))
        assertEquals(VpnPillAvailability.UNKNOWN, vpnPillAvailability(true, true, true, 2, null))
    }
    @Test fun connectingNeverClaimsAnEstablishedTunnel() {
        assertEquals("…", vpnLiveUpdateText(false, 0, false))
    }
    @Test fun justConnectedShowsBriefStatusThenOnlyTheCloud() {
        assertEquals("VPN", vpnLiveUpdateText(true, 0, false))
        assertEquals("VPN", vpnLiveUpdateText(true, 5, false))
        assertNull(vpnLiveUpdateText(true, 6, false))
        assertNull(vpnLiveUpdateText(true, 3600, false))
    }
    @Test fun recoveryOverridesOldConnectionDuration() {
        assertEquals("…", vpnLiveUpdateText(true, 3600, true))
        assertEquals("…", vpnLiveUpdateText(false, 3600, true))
    }
}
