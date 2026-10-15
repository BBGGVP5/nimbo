package com.danila.nimbo.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsDiscoveryTest {
    @Test fun blankSearchRespectsMode() {
        assertFalse(matchesSettingsEntry(" \n ", "DNS TLS", true, false))
        assertTrue(matchesSettingsEntry("", "DNS TLS", true, true))
        assertTrue(matchesSettingsEntry("", "Диагностика", false, false))
    }
    @Test fun searchIncludesAdvancedEntriesWithoutEnablingThem() {
        assertTrue(matchesSettingsEntry("  dNs\tTLS ", "DNS и транспорт TLS", true, false))
        assertTrue(matchesSettingsEntry("ЯДРО", "Ядро VPN Xray Mihomo AWG", true, false))
        assertFalse(matchesSettingsEntry("dns ядро", "DNS и транспорт TLS", true, false))
        assertFalse(matchesSettingsEntry("несуществующее", "Диагностика", false, true))
    }
}
