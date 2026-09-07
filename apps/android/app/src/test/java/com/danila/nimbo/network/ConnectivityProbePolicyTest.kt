package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectivityProbePolicyTest {
    @Test
    fun `domain uses secure default port`() {
        val target = ConnectivityProbePolicy.parseTarget("ya.ru")

        assertEquals("ya.ru", target?.host)
        assertEquals(443, target?.port)
        assertEquals("https://ya.ru", target?.url)
    }

    @Test
    fun `url preserves path and explicit scheme`() {
        val target = ConnectivityProbePolicy.parseTarget("http://example.com/status")

        assertEquals("example.com", target?.host)
        assertEquals(80, target?.port)
        assertEquals("http://example.com/status", target?.url)
    }

    @Test
    fun `ipv4 and custom port are supported`() {
        val target = ConnectivityProbePolicy.parseTarget("1.1.1.1:853")

        assertEquals("1.1.1.1", target?.host)
        assertEquals(853, target?.port)
    }

    @Test
    fun `credentials and invalid input are rejected`() {
        assertNull(ConnectivityProbePolicy.parseTarget("https://user:pass@example.com"))
        assertNull(ConnectivityProbePolicy.parseTarget("not a host"))
        assertNull(ConnectivityProbePolicy.parseTarget("https://example.com:99999"))
    }
}
