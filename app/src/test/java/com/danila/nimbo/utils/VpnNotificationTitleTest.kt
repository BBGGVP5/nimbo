package com.danila.nimbo.utils

import com.danila.nimbo.ui.screens.SubscriptionProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnNotificationTitleTest {
    @Test fun numberedMihomoMarkerUsesSubscriptionName() {
        val parent = SubscriptionProfile(url = "https://example.invalid/sub", name = "BBGGVP5")
        val mihomo = SubscriptionProfile(url = "mihomo://one", name = "Mihomo 1",
            configType = "mihomo", mihomoParentUrl = parent.url)
        assertEquals("BBGGVP5", vpnNotificationProfileName(listOf(parent, mihomo), mihomo))
        assertEquals("BBGGVP5", vpnNotificationTitle("Mihomo 1", "BBGGVP5", true))
    }

    @Test fun customNativeNameWinsAndGenericMarkerNeverLeaks() {
        val profile = SubscriptionProfile(url = "mihomo://one", name = "Mihomo 1",
            customName = "Мой YAML", configType = "mihomo")
        assertEquals("Мой YAML", vpnNotificationProfileName(listOf(profile), profile))
        assertEquals("Mihomo", vpnNotificationTitle("Mihomo 1", null, true))
    }

    @Test fun regularServerTitleIsUnchanged() {
        assertEquals("Финляндия", vpnNotificationTitle("Финляндия", "BBGGVP5", false))
    }
}
