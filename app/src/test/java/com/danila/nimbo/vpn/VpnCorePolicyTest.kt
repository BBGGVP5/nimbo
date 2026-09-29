package com.danila.nimbo.vpn

import com.danila.nimbo.model.Server
import org.junit.Assert.*
import org.junit.Test

class VpnCorePolicyTest {
    private fun server(protocol: String) = Server("Test", "example.test", 443, "id", protocol)

    @Test fun autoPreservesExistingXrayAndAwgDispatch() {
        listOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "naive", "awg", "amneziawg", "wireguard")
            .forEach { assertNull(it, VpnCorePolicy.rejection("auto", server(it))) }
    }

    @Test fun explicitCoreNeverFallsBackToAnotherEngine() {
        assertNull(VpnCorePolicy.rejection("xray", server("vless")))
        assertNull(VpnCorePolicy.rejection("awg", server("wireguard")))
        assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE, VpnCorePolicy.rejection("xray", server("awg")))
        assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE, VpnCorePolicy.rejection("awg", server("vless")))
    }

    private fun mihomo() = server("mihomo").copy(host = "mihomo.invalid", profileUrl = "profile:test")

    @Test fun nativeProfileRequiresCompiledAdapterAndMatchingEngine() {
        assertNull(VpnCorePolicy.rejection("auto", mihomo(), mihomoAvailable = true))
        assertNull(VpnCorePolicy.rejection("mihomo", mihomo(), mihomoAvailable = true))
        assertEquals(VpnCorePolicy.Rejection.UNAVAILABLE,
            VpnCorePolicy.rejection("auto", mihomo(), mihomoAvailable = false))
        for (core in listOf("xray", "awg")) assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE,
            VpnCorePolicy.rejection(core, mihomo(), mihomoAvailable = true))
        assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE,
            VpnCorePolicy.rejection("mihomo", server("vless"), mihomoAvailable = true))
    }

    @Test fun yamlAliasesAndForgedMarkersNeverReachXray() {
        for (protocol in listOf("clash", "clash-meta", "yaml", "mihomo"))
            assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE,
                VpnCorePolicy.rejection("auto", server(protocol), mihomoAvailable = true))
        assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE,
            VpnCorePolicy.rejection("auto", mihomo().copy(profileUrl = null), mihomoAvailable = true))
        assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE,
            VpnCorePolicy.rejection("auto", mihomo().copy(uuid = ""), mihomoAvailable = true))
    }

    @Test fun unknownPreferenceFailsClosedRatherThanBecomingAuto() {
        assertEquals(VpnCorePolicy.Rejection.UNKNOWN_CORE, VpnCorePolicy.rejection("future-core", server("vless")))
        assertNull(VpnCorePolicy.rejection(" XRAY ", server("vless")))
    }

    @Test fun autoAndRotationCandidateFilteringCannotEscapeSelectedCore() {
        val vless = server("vless")
        val awg = server("awg")
        assertEquals(listOf(vless), VpnCorePolicy.candidates("xray", listOf(awg, vless)))
        assertEquals(listOf(awg), VpnCorePolicy.candidates("awg", listOf(vless, awg)))
        assertTrue(VpnCorePolicy.candidates("mihomo", listOf(vless, awg)).isEmpty())
        assertTrue(VpnCorePolicy.candidates("unknown", listOf(vless, awg)).isEmpty())
    }

    @Test fun nativeXrayTemplatesCannotBeSentToAwgIniRunner() {
        assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE,
            VpnCorePolicy.rejection("auto", server("wireguard").copy(host = "api", uuid = "remote")))
        assertEquals(VpnCorePolicy.Rejection.INCOMPATIBLE,
            VpnCorePolicy.rejection("awg", server("awg").copy(templateUuid = "subscription-json:id")))
        assertNull(VpnCorePolicy.rejection("xray", server("vless").copy(host = "api", uuid = "remote")))
    }
}
