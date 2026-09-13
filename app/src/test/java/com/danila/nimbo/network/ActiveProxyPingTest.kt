package com.danila.nimbo.network

import com.danila.nimbo.vpn.HealthProxySession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ActiveProxyPingTest {
    private class ProxyFixture(val reply: (Socket) -> Unit) : AutoCloseable {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val request = CompletableDeferred<String>()
        val executor = Executors.newSingleThreadExecutor()
        @Volatile var accepted: Socket? = null
        init {
            executor.submit {
                try {
                    server.accept().use { socket ->
                        accepted = socket
                        socket.soTimeout = 3000
                        val reader = socket.getInputStream().bufferedReader()
                        val lines = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            lines += line
                        }
                        request.complete(lines.joinToString("\n"))
                        reply(socket)
                    }
                } catch (error: Exception) { request.completeExceptionally(error) }
            }
        }
        override fun close() {
            accepted?.close()
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(3, TimeUnit.SECONDS)
        }
    }

    private fun respond(socket: Socket, status: String = "204 No Content", extra: String = "") {
        socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: 0\r\n$extra\r\n".toByteArray())
        socket.getOutputStream().flush()
    }

    @Test fun `real GET reaches explicit authenticated proxy without origin DNS`() = runBlocking {
        val session = HealthProxySession("node-A")
        ProxyFixture({ respond(it) }).use { proxy ->
            val result = ActiveProxyPing.measure("http://does-not-exist.invalid/health?q=1", 1000, session, { true }, proxyPort = proxy.server.localPort)
            assertTrue(result >= 0)
            val request = proxy.request.await()
            assertTrue(request.startsWith("GET http://does-not-exist.invalid/health?q=1 HTTP/1.1"))
            assertTrue(request.contains("Proxy-Authorization: ${Credentials.basic(session.username, session.password)}"))
            assertTrue(request.contains("Cache-Control: no-cache, no-store"))
            assertFalse(request.contains("Range:"))
        }
    }

    @Test fun `reject redirects auth refusal and error statuses`() = runBlocking {
        for (status in listOf("302 Found", "407 Proxy Authentication Required", "500 Error")) {
            ProxyFixture({ respond(it, status, "Location: http://must-not-follow.invalid/\r\n") }).use { proxy ->
                assertEquals(-1, ActiveProxyPing.measure("http://example.invalid/", 700, HealthProxySession("a"), { true }, proxyPort = proxy.server.localPort))
            }
        }
    }

    @Test fun `HTTPS authenticates CONNECT and refuses invalid TLS`() = runBlocking {
        val session = HealthProxySession("a")
        ProxyFixture({ respond(it, "200 Connection Established") }).use { proxy ->
            assertEquals(-1, ActiveProxyPing.measure("https://tls.invalid/", 700, session, { true }, proxyPort = proxy.server.localPort))
            val request = proxy.request.await()
            assertTrue(request.startsWith("CONNECT tls.invalid:443 HTTP/1.1"))
            assertTrue(request.contains("Proxy-Authorization: ${Credentials.basic(session.username, session.password)}"))
            assertFalse(request.contains("GET "))
        }
    }

    @Test fun `stale session suppresses otherwise successful response`() = runBlocking {
        val current = AtomicBoolean(true)
        ProxyFixture({ current.set(false); respond(it) }).use { proxy ->
            assertEquals(-1, ActiveProxyPing.measure("http://example.invalid/", 700, HealthProxySession("a"), { current.get() }, proxyPort = proxy.server.localPort))
        }
    }

    @Test fun `unavailable and invalid URLs perform no IO`() = runBlocking {
        val session = HealthProxySession("a")
        assertEquals(-1, ActiveProxyPing.measure("http://example.invalid/", 700, session, { false }))
        for (url in listOf("", "file:///tmp/a", "ftp://example.com", "https://user:pass@example.com", "https://example.com/#fragment", "https://{host}/")) {
            assertFalse(url, ActiveProxyPing.validUrl(url))
            assertEquals(-1, ActiveProxyPing.measure(url, 700, session, { true }))
        }
    }

    @Test fun `whole call deadline bounds a silent proxy`() = runBlocking {
        ProxyFixture({ it.getInputStream().read() }).use { proxy ->
            val start = System.nanoTime()
            assertEquals(-1, ActiveProxyPing.measure("http://example.invalid/", 250, HealthProxySession("a"), { true }, proxyPort = proxy.server.localPort))
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000)
        }
    }

    @Test fun `coroutine cancellation closes the active call promptly`() = runBlocking {
        ProxyFixture({ it.getInputStream().read() }).use { proxy ->
            val call = async { ActiveProxyPing.measure("http://example.invalid/", 10000, HealthProxySession("a"), { true }, proxyPort = proxy.server.localPort) }
            proxy.request.await()
            val start = System.nanoTime()
            call.cancelAndJoin()
            assertTrue(call.isCancelled)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
        }
    }
}
