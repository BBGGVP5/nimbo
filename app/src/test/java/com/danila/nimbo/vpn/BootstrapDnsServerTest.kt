package com.danila.nimbo.vpn

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BootstrapDnsServerTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private fun query(id: Int = 17): ByteArray = byteArrayOf(
        0, id.toByte(), 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        4, 'n'.code.toByte(), 'o'.code.toByte(), 'd'.code.toByte(), 'e'.code.toByte(),
        4, 't'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 0, 0, 1, 0, 1
    )
    private fun answer(query: ByteArray): ByteArray = query.copyOf().apply { this[2] = 0x81.toByte(); this[3] = 0x80.toByte() }
    private fun exchange(resolve: (ByteArray) -> ByteArray?) = object : BootstrapDnsExchange {
        override fun resolve(query: ByteArray) = resolve.invoke(query)
        override fun close() = Unit
    }
    private fun udp(port: Int, query: ByteArray): ByteArray = DatagramSocket().use { socket ->
        socket.soTimeout = 2_000
        socket.send(DatagramPacket(query, query.size, loopback, port))
        val reply = DatagramPacket(ByteArray(65_535), 65_535)
        socket.receive(reply)
        reply.data.copyOf(reply.length)
    }
    private fun tcp(port: Int, queries: List<ByteArray>): List<ByteArray> = Socket(loopback, port).use { socket ->
        socket.soTimeout = 2_000
        val output = DataOutputStream(socket.getOutputStream())
        val input = DataInputStream(socket.getInputStream())
        queries.map { query ->
            output.writeShort(query.size); output.write(query); output.flush()
            ByteArray(input.readUnsignedShort()).also(input::readFully)
        }
    }

    @Test fun udpPreservesQuestionAndTransactionId() {
        BootstrapDnsServer(exchange { answer(it) }).use { server ->
            assertEquals("127.0.0.1:${server.port}", server.endpoint)
            assertArrayEquals(answer(query()), udp(server.port, query()))
        }
    }

    @Test fun tcpFramesMultipleQueriesOnOneConnection() {
        BootstrapDnsServer(exchange { answer(it) }).use { server ->
            val requests = listOf(query(19), query(20))
            val replies = tcp(server.port, requests)
            requests.indices.forEach { assertArrayEquals(answer(requests[it]), replies[it]) }
        }
    }

    @Test fun unavailableResolverReturnsServfailWithoutFallback() {
        BootstrapDnsServer(exchange { null }).use { server ->
            val reply = udp(server.port, query())
            assertEquals(2, reply[3].toInt() and 0x0f)
            assertEquals(17, reply[1].toInt())
            assertArrayEquals(query().copyOfRange(12, query().size), reply.copyOfRange(12, reply.size))
        }
    }

    @Test fun malformedQueriesAreDroppedBeforePlatformDns() {
        val calls = AtomicInteger()
        BootstrapDnsServer(exchange { calls.incrementAndGet(); answer(it) }).use { server ->
            DatagramSocket().use { socket ->
                // QR=1 is a response, not a resolver request.
                val invalid = answer(query())
                socket.send(DatagramPacket(invalid, invalid.size, loopback, server.port))
                val short = byteArrayOf(0, 1, 2)
                socket.send(DatagramPacket(short, short.size, loopback, server.port))
                // A valid request behind those packets proves both were processed.
                socket.send(DatagramPacket(query(), query().size, loopback, server.port))
                socket.soTimeout = 2_000
                socket.receive(DatagramPacket(ByteArray(512), 512))
            }
            assertEquals(1, calls.get())
        }
    }

    @Test fun newSessionHasNoPreviousNetworkAnswers() {
        val first = AtomicInteger(); val second = AtomicInteger()
        BootstrapDnsServer(exchange { first.incrementAndGet(); answer(it) }).use { udp(it.port, query()) }
        BootstrapDnsServer(exchange { second.incrementAndGet(); answer(it) }).use { udp(it.port, query()) }
        assertEquals(1, first.get()); assertEquals(1, second.get())
    }

    @Test fun platformTransactionIdIsMappedBackToNativeQuery() {
        BootstrapDnsServer(exchange { answer(it).apply { this[0] = 99; this[1] = 98 } }).use { server ->
            assertArrayEquals(answer(query()), udp(server.port, query()))
        }
    }

    @Test fun thrownResolverErrorDoesNotKillListener() {
        val calls = AtomicInteger()
        BootstrapDnsServer(exchange {
            if (calls.incrementAndGet() == 1) error("Network changed") else answer(it)
        }).use { server ->
            assertEquals(2, udp(server.port, query())[3].toInt() and 0x0f)
            assertArrayEquals(answer(query(18)), udp(server.port, query(18)))
        }
    }

    @Test fun truncatedUdpAnswerRemainsAvailableForTcpRetry() {
        BootstrapDnsServer(exchange { answer(it).apply { this[2] = 0x83.toByte() } }).use { server ->
            val udpReply = udp(server.port, query())
            val tcpReply = tcp(server.port, listOf(query())).single()
            assertEquals(2, udpReply[2].toInt() and 2)
            assertArrayEquals(udpReply, tcpReply)
        }
    }

    @Test fun closeCancelsExchangeAndReleasesTcpListener() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val closed = AtomicBoolean()
        val server = BootstrapDnsServer(object : BootstrapDnsExchange {
            override fun resolve(query: ByteArray): ByteArray? {
                started.countDown(); release.await(2, TimeUnit.SECONDS); return null
            }
            override fun close() { closed.set(true); release.countDown() }
        })
        try {
            DatagramSocket().use { socket -> socket.send(DatagramPacket(query(), query().size, loopback, server.port)) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            server.close(); server.close()
            assertTrue(closed.get())
            assertTrue(runCatching { Socket(loopback, server.port).close() }.isFailure)
        } finally { server.close() }
    }

    @Test fun stopCancelsPendingDnsBeforeNativeShutdownWithoutRemovingListener() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val client = Executors.newSingleThreadExecutor()
        BootstrapDnsServer(object : BootstrapDnsExchange {
            override fun resolve(query: ByteArray): ByteArray? {
                started.countDown(); release.await(2, TimeUnit.SECONDS); return null
            }
            override fun close() { release.countDown() }
        }).use { server ->
            try {
                val reply = client.submit<ByteArray> { udp(server.port, query()) }
                assertTrue(started.await(2, TimeUnit.SECONDS))
                server.stopResolving()
                assertEquals(2, reply.get(1, TimeUnit.SECONDS)[3].toInt() and 0x0f)
                // Native resolver retries during stop receive an error, not a lost listener.
                assertEquals(2, udp(server.port, query(18))[3].toInt() and 0x0f)
            } finally { release.countDown(); client.shutdownNow() }
        }
    }
}
