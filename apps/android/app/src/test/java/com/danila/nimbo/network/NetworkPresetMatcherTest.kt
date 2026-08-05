package com.danila.nimbo.network

import com.danila.nimbo.model.NetworkPreset
import com.danila.nimbo.utils.NetworkPresetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkPresetMatcherTest {
    @Test
    fun exactSsidWinsOverGenericWifiPreset() {
        val generic = preset("generic", NetworkPresetType.HOME)
        val exact = preset("exact", NetworkPresetType.OTHER).copy(
            matchSsid = "Nimbo Home",
            matchTransport = "wifi"
        )
        val result = NetworkPresetMatcher.bestMatch(
            listOf(generic, exact),
            NetworkContextSnapshot(
                transport = NetworkTransport.WIFI,
                ssid = "nimbo home",
                metered = false,
                validated = true
            )
        )
        assertEquals("exact", result?.preset?.id)
    }

    @Test
    fun explicitRuleDoesNotMatchWhenRequiredContextIsUnknown() {
        val preset = preset("carrier", NetworkPresetType.OTHER).copy(matchCarrierName = "MTS")
        assertNull(
            NetworkPresetMatcher.match(
                preset,
                NetworkContextSnapshot(transport = NetworkTransport.CELLULAR)
            )
        )
    }

    @Test
    fun captivePortalSelectsPublicWifiFallback() {
        val result = NetworkPresetMatcher.bestMatch(
            listOf(
                preset("home", NetworkPresetType.HOME),
                preset("public", NetworkPresetType.PUBLIC_WIFI)
            ),
            NetworkContextSnapshot(
                transport = NetworkTransport.WIFI,
                metered = false,
                captivePortal = true
            )
        )
        assertEquals("public", result?.preset?.id)
    }

    private fun preset(id: String, type: NetworkPresetType) = NetworkPreset(
        id = id,
        name = id,
        type = type
    )
}
