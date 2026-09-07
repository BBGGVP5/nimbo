package com.danila.nimbo.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class LogPresentationTest {

    @Test
    fun vpnServiceTag_hasUserFacingEnglishSource() {
        assertEquals("VPN", LogPresentation.source("MyVpnService", isEnglish = true))
    }

    @Test
    fun subscriptionTag_hasLocalizedRussianSource() {
        assertEquals("Подписки", LogPresentation.source("SubscriptionManager", isEnglish = false))
    }
}
