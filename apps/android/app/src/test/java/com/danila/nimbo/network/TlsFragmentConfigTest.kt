package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TlsFragmentConfigTest {
    @Test
    fun `parses provider key value contract`() {
        assertEquals(
            TlsFragmentConfig(
                enabled = true,
                packets = "tlshello",
                length = "100-200",
                interval = "10-20"
            ),
            TlsFragmentConfig.parse(
                "enabled=true; packets=tlshello; length=100-200; interval=10-20"
            )
        )
    }

    @Test
    fun `parses compact contract and normalizes ranges`() {
        assertEquals(
            TlsFragmentConfig(true, "tlshello", "100-200", "10-20"),
            TlsFragmentConfig.parse("tlshello,200-100,20-10")
        )
    }

    @Test
    fun `off is an explicit provider override`() {
        assertEquals(
            TlsFragmentConfig(enabled = false),
            TlsFragmentConfig.parse("off")
        )
    }

    @Test
    fun `invalid values are rejected instead of reaching xray`() {
        assertNull(TlsFragmentConfig.parse("enabled=true; length=broken"))
        assertNull(TlsFragmentConfig.parse("enabled=true; length=1-5000"))
        assertNull(TlsFragmentConfig.parse("enabled=true; packets=http"))
    }

    @Test
    fun `blank header is treated as absent`() {
        assertNull(TlsFragmentConfig.parse("  "))
    }
}
