package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Test

class PingManagerTest {

    @Test
    fun aggregateTcpSamples_dropsSlowOutlierAndUsesMedian() {
        val result = PingManager.aggregateTcpSamples(listOf(31, 30, 250, 32))

        assertEquals(31, result)
    }

    @Test
    fun aggregateTcpSamples_averagesTwoMiddleSamplesForEvenCount() {
        val result = PingManager.aggregateTcpSamples(listOf(40, 44))

        assertEquals(42, result)
    }

    @Test
    fun aggregateTcpSamples_returnsMinusOneWhenNoSuccessfulSamples() {
        val result = PingManager.aggregateTcpSamples(emptyList())

        assertEquals(-1, result)
    }

    @Test
    fun httpPing_keepsConfiguredHealthUrlWhenItHasNoServerTemplate() {
        val url = PingManager.resolveHttpUrl(
            host = "edge.example",
            port = 443,
            templateUrl = "https://www.gstatic.com/generate_204"
        )

        assertEquals("https://www.gstatic.com/generate_204", url)
    }

    @Test
    fun httpPing_expandsAnExplicitServerTemplate() {
        val url = PingManager.resolveHttpUrl(
            host = "edge.example",
            port = 8443,
            templateUrl = "https://{host}:{port}/health"
        )

        assertEquals("https://edge.example:8443/health", url)
    }

    @Test
    fun proxyMode_convertsDirectOnlyProtocolsToHttpHead() {
        assertEquals(
            PingProtocol.HTTP_HEAD,
            PingManager.effectiveProtocol(PingConfig(protocol = PingProtocol.TCP, useProxy = true))
        )
        assertEquals(
            PingProtocol.HTTP_HEAD,
            PingManager.effectiveProtocol(PingConfig(protocol = PingProtocol.ICMP, useProxy = true))
        )
        assertEquals(
            PingProtocol.TCP,
            PingManager.effectiveProtocol(PingConfig(protocol = PingProtocol.TCP, useProxy = false))
        )
        assertEquals(
            PingProtocol.HTTP_HEAD,
            PingManager.effectiveProtocol(PingConfig(protocol = PingProtocol.HTTPS_STRICT, useProxy = true))
        )
    }

    @Test
    fun hysteriaDefaultPing_triesIcmpBeforeTcp() {
        assertEquals(
            listOf(PingProtocol.ICMP, PingProtocol.TCP),
            PingManager.protocolAttempts(
                config = PingConfig(protocol = PingProtocol.TCP, useProxy = false),
                serverProtocol = "hysteria",
                network = "hysteria"
            )
        )
    }

    @Test
    fun ordinaryTcpPing_fallsBackToIcmp() {
        assertEquals(
            listOf(PingProtocol.TCP, PingProtocol.ICMP),
            PingManager.protocolAttempts(
                config = PingConfig(protocol = PingProtocol.TCP, useProxy = false),
                serverProtocol = "vless",
                network = "tcp"
            )
        )
    }

    @Test
    fun proxyPing_doesNotMixInDirectFallbacks() {
        assertEquals(
            listOf(PingProtocol.HTTP_HEAD),
            PingManager.protocolAttempts(
                config = PingConfig(protocol = PingProtocol.TCP, useProxy = true),
                serverProtocol = "hysteria",
                network = "hysteria"
            )
        )
    }
}
