package com.danila.nimbo.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaiveServerIdentityTest {
    @Test
    fun `Naive credentials identify transport without exposing password`() {
        val https = Server(
            name = "Naive HTTPS",
            host = "edge.example",
            port = 443,
            uuid = "alice",
            protocol = "naive",
            network = "https",
            naiveUsername = "alice",
            naivePassword = "do-not-log-me",
            naiveTransport = "https"
        )
        val quic = https.copy(network = "quic", naiveTransport = "quic")

        assertTrue(https.isNaiveProxy())
        assertNotEquals(https.pingKey(), quic.pingKey())
        assertFalse(https.pingKey().contains("do-not-log-me"))
        assertFalse(https.selectionKey().contains("do-not-log-me"))
    }
}
