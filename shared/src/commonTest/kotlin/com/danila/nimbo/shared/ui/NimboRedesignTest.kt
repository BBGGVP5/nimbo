package com.danila.nimbo.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NimboRedesignTest {
    @Test fun monochromeDefaultHasContrastInBothSchemes() {
        val dark = nimboColors(NimboAppearance(themeMode = "dark"), false)
        val light = nimboColors(NimboAppearance(themeMode = "light"), true)
        assertEquals(Color(0xFF121212), dark.background)
        assertEquals(Color(0xFFF5F4F1), light.background)
        assertTrue(dark.accent.luminance() > .7f)
        assertTrue(light.accent.luminance() < .05f)
        assertEquals(dark.surface.red, dark.surface.green)
        assertEquals(dark.surface.green, dark.surface.blue)
    }

    @Test fun explicitUserAccentIsPreserved() {
        assertEquals(Color(0xFF75A7FF), nimboColors(NimboAppearance(accentHex = "75A7FF"), true).accent)
    }

    @Test fun stateLabelsNeverClaimProtectionDuringFailureOrTransition() {
        for (status in listOf("idle", "failed", "preparing", "connecting", "disconnecting")) {
            assertFalse(NimboUiState(vpnState = status).connectionTitle == "Вы подключены")
        }
        assertTrue(NimboUiState(vpnState = "connecting").connectionBusy)
        assertFalse(NimboUiState(vpnState = "failed").connectionBusy)
        assertEquals("Вы подключены", NimboUiState(vpnState = "connected").connectionTitle)
    }

    @Test fun profilesSearchAndSortPreserveMeasuredZeroAndFavoriteFilter() {
        val servers = listOf(
            NimboServerUi("slow", "Berlin", "vless", ping = 90),
            NimboServerUi("zero", "Amsterdam", "vless", ping = 0),
            NimboServerUi("none", "Amsterdam reserve", "trojan", ping = -1))
        val state = NimboUiState(servers = servers, serverSort = "ping", favoritesFirst = false, favoriteServerIds = setOf("none"))
        assertEquals(listOf("zero", "none"), filterAndSortServers(state, " amsterdam ", false).map { it.id })
        assertEquals(listOf("none"), filterAndSortServers(state, "", true).map { it.id })
        assertTrue(filterAndSortServers(state, "missing", false).isEmpty())
        assertEquals(0, servers[1].ping)
    }

    @Test fun defaultHasNoDecorativeBackgroundWork() {
        val state = NimboUiState()
        assertFalse(state.backgroundMotion)
        assertFalse(state.statusParticles)
    }

    @Test fun connectionButtonUsesCloudOnlyForEstablishedConnection() {
        for (status in listOf("idle", "failed", "preparing", "connecting", "disconnecting")) {
            assertEquals(NimboIconName.POWER, NimboUiState(vpnState = status).connectionIcon)
        }
        assertEquals(NimboIconName.CLOUD, NimboUiState(vpnState = "connected").connectionIcon)
    }

    @Test fun decorativeCaptionsCycleWithoutClaimingCompletion() {
        assertTrue(NimboBusyKind.CONNECTION.captions.size >= 9)
        for (kind in NimboBusyKind.entries) {
            assertTrue(kind.captions.size > 1)
            assertEquals(busyCaption(kind, 0), busyCaption(kind, kind.captions.size))
            assertTrue(kind.captions.all { it.isNotBlank() && !it.contains("100%") })
        }
    }
    @Test fun timeoutInputUsesSecondsWithoutChangingMeasurementUnits() {
        assertEquals(1500, pingTimeoutMillisFromSeconds("1,5"))
        assertEquals(2750, pingTimeoutMillisFromSeconds("2.75"))
        assertEquals(10000, pingTimeoutMillisFromSeconds("10"))
        for (invalid in listOf("", "0", "1000", "10.1", "NaN", "Infinity", "wrong")) {
            assertEquals(null, pingTimeoutMillisFromSeconds(invalid))
        }
        assertEquals("84 ms", pingDisplayLabel(84, false, "tcp"))
    }

    @Test fun quotaOnlyUsesKnownProviderLimitAndClampsExhaustedTraffic() {
        assertEquals(null, NimboUiState(profileTrafficUsed = 80).remainingQuotaFraction)
        assertEquals(.4f, NimboUiState(profileTrafficUsed = 60, profileTrafficTotal = 100).remainingQuotaFraction)
        assertEquals(0f, NimboUiState(profileTrafficUsed = 120, profileTrafficTotal = 100).remainingQuotaFraction)
        assertEquals(1f, NimboUiState(profileTrafficUsed = -1, profileTrafficTotal = 100).remainingQuotaFraction)
    }
}
