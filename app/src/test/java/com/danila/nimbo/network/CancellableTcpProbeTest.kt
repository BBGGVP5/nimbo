package com.danila.nimbo.network

import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CancellableTcpProbeTest {
    @Test fun `cancelling closes pending socket without waiting for timeout or publishing failure`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val closed = CountDownLatch(1)
        var published = false
        val socket = object : Socket() {
            override fun setTcpNoDelay(on: Boolean) = Unit
            override fun connect(endpoint: SocketAddress, timeout: Int) {
                started.complete(Unit)
                check(closed.await(2, TimeUnit.SECONDS)) { "Cancelled probe did not close its socket" }
                throw SocketException("closed")
            }
            override fun close() { closed.countDown() }
        }
        val job = launch {
            cancellableTcpProbe(InetSocketAddress("127.0.0.1", 9), 10_000) { socket }
            published = true
        }
        started.await()
        withTimeout(1000) { job.cancelAndJoin() }
        assertEquals(0L, closed.count)
        assertFalse(published)
    }

    @Test fun `success and failure both release the owned socket`() = runBlocking {
        for (fail in listOf(false, true)) {
            var closed = false
            val result = cancellableTcpProbe(InetSocketAddress("127.0.0.1", 9), 500) {
                object : Socket() {
                    override fun setTcpNoDelay(on: Boolean) = Unit
                    override fun connect(endpoint: SocketAddress, timeout: Int) { if (fail) throw SocketException("unavailable") }
                    override fun close() { closed = true }
                }
            }
            assertTrue(closed)
            if (fail) assertNull(result) else assertTrue(result!! >= 1)
        }
    }
}
