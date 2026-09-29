package com.danila.nimbo.awg

import com.danila.nimbo.model.Server
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwgConfigBuilderTest {

    private fun awgServer() = Server(
        name = "AWG RU",
        host = "vpn.example.com",
        port = 51820,
        uuid = "",
        protocol = "awg",
        wgPrivateKey = "privKey123",
        wgPublicKey = "pubKey456",
        wgPresharedKey = "psk789",
        wgAllowedIps = "0.0.0.0/0, ::/0",
        wgKeepAlive = 25,
        awgJc = 5,
        awgJmin = 40,
        awgJmax = 70,
        awgS1 = 1,
        awgS2 = 2,
        awgS3 = 3,
        awgS4 = 4,
        awgH1 = "1-2",
        awgH2 = "3-4",
        awgI5 = "100"
    )

    @Test
    fun buildSettings_containsKeysEndpointAndObfuscation() {
        val settings = AwgConfigBuilder.buildSettings(awgServer())!!

        assertTrue(settings.contains("private_key=privKey123"))
        assertTrue(settings.contains("public_key=pubKey456"))
        assertTrue(settings.contains("preshared_key=psk789"))
        assertTrue(settings.contains("endpoint=vpn.example.com:51820"))
        assertTrue(settings.contains("persistent_keepalive_interval=25"))
        assertTrue(settings.contains("allowed_ip=0.0.0.0/0"))
        assertTrue(settings.contains("allowed_ip=::/0"))
        assertTrue(settings.contains("jc=5"))
        assertTrue(settings.contains("jmin=40"))
        assertTrue(settings.contains("jmax=70"))
        assertTrue(settings.contains("s1=1"))
        assertTrue(settings.contains("s4=4"))
        assertTrue(settings.contains("h1=1-2"))
        assertTrue(settings.contains("i5=100"))
        assertFalse(settings.contains("private_key=null"))
    }

    @Test
    fun buildSettings_defaultsAllowedIpsToFullRoute() {
        val server = awgServer().copy(wgAllowedIps = null)
        val settings = AwgConfigBuilder.buildSettings(server)!!

        assertTrue(settings.contains("allowed_ip=0.0.0.0/0"))
        assertTrue(settings.contains("allowed_ip=::/0"))
    }

    @Test
    fun buildSettings_skipsObfuscationWhenAbsent() {
        val server = awgServer().copy(
            awgJc = null,
            awgJmin = null,
            awgJmax = null,
            awgS1 = null,
            awgS2 = null,
            awgS3 = null,
            awgS4 = null,
            awgH1 = null,
            awgH2 = null,
            awgH3 = null,
            awgH4 = null,
            awgI1 = null,
            awgI2 = null,
            awgI3 = null,
            awgI4 = null,
            awgI5 = null
        )
        val settings = AwgConfigBuilder.buildSettings(server)!!

        assertFalse(settings.contains("jc="))
        assertFalse(settings.contains("jmin="))
        assertFalse(settings.contains("h1="))
        assertTrue(settings.contains("private_key=privKey123"))
    }

    @Test
    fun buildSettings_requiresBothKeys() {
        assertNull(AwgConfigBuilder.buildSettings(awgServer().copy(wgPrivateKey = null)))
        assertNull(AwgConfigBuilder.buildSettings(awgServer().copy(wgPublicKey = "")))
        assertNull(AwgConfigBuilder.buildSettings(awgServer().copy(wgPrivateKey = "  ")))
    }

    @Test
    fun buildSettings_omitsKeepAliveWhenNotPositive() {
        val settings = AwgConfigBuilder.buildSettings(awgServer().copy(wgKeepAlive = null))!!
        assertFalse(settings.contains("persistent_keepalive_interval="))
    }
}
