package com.danila.nimbo.vpn

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal interface BootstrapDnsExchange : Closeable {
    /** Raw platform answer; null means unavailable, never a public-DNS fallback. */
    fun resolve(query: ByteArray): ByteArray?
}

/** Only the native system resolver uses this adapter; subscription DNS is untouched. */
internal class BootstrapDnsServer(private val exchange: BootstrapDnsExchange) : Closeable {
    private val closed = AtomicBoolean()
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(
        4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        { work -> Thread(work, "nimbo-bootstrap-dns-worker").apply { isDaemon = true } }
    )
    private val tcp = ServerSocket()
    private val udp = DatagramSocket(null)
    val port: Int get() = tcp.localPort
    val endpoint: String get() = "127.0.0.1:$port"

    init {
        try {
            val loopback = InetAddress.getByName("127.0.0.1")
            tcp.bind(InetSocketAddress(loopback, 0), 4)
            udp.bind(InetSocketAddress(loopback, port))
            thread(name = "nimbo-bootstrap-dns-udp", isDaemon = true) { receiveUdp() }
            thread(name = "nimbo-bootstrap-dns-tcp", isDaemon = true) { receiveTcp() }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    private fun receiveUdp() {
        while (!closed.get()) {
            try {
                val packet = DatagramPacket(ByteArray(65_535), 65_535)
                udp.receive(packet)
                val query = packet.data.copyOf(packet.length)
                if (questionEnd(query) == null) continue
                dispatch(onRejected = { sendUdp(failure(query), packet) }) {
                    sendUdp(resolve(query), packet)
                }
            } catch (_: Exception) {
                // A closed listener must not spin or keep the retired network alive.
                if (!closed.get()) close()
            }
        }
    }

    private fun sendUdp(answer: ByteArray, request: DatagramPacket) {
        if (!closed.get()) runCatching {
            udp.send(DatagramPacket(answer, answer.size, request.socketAddress))
        }
    }

    private fun receiveTcp() {
        while (!closed.get()) {
            try {
                val client = tcp.accept()
                clients.add(client)
                if (closed.get()) { clients.remove(client); client.close(); continue }
                client.soTimeout = 5_000
                dispatch(onRejected = { clients.remove(client); client.close() }) {
                    try {
                        client.use {
                            val input = DataInputStream(it.getInputStream())
                            val output = DataOutputStream(it.getOutputStream())
                            // Go retries truncated UDP over TCP and may reuse a connection.
                            while (!closed.get()) {
                                val size = input.readUnsignedShort()
                                if (size < 12) break
                                val query = ByteArray(size).also(input::readFully)
                                if (questionEnd(query) == null) break
                                val answer = resolve(query)
                                output.writeShort(answer.size)
                                output.write(answer)
                                output.flush()
                            }
                        }
                    } finally { clients.remove(client) }
                }
            } catch (_: Exception) {
                if (!closed.get()) close()
            }
        }
    }

    private fun dispatch(onRejected: () -> Unit, work: () -> Unit) {
        try {
            workers.execute { runCatching { if (!closed.get()) work() } }
        } catch (_: RejectedExecutionException) { runCatching(onRejected) }
    }

    private fun resolve(query: ByteArray): ByteArray {
        val answer = runCatching { exchange.resolve(query) }.getOrNull()
        if (answer == null || answer.size !in 12..65_507 || answer[2].toInt() and 0x80 == 0) {
            return failure(query)
        }
        // Android may allocate a different DNS ID. Preserve the native caller's ID.
        return answer.copyOf().apply { this[0] = query[0]; this[1] = query[1] }
    }

    private fun failure(query: ByteArray): ByteArray = query.copyOf(requireNotNull(questionEnd(query))).apply {
        this[2] = (0x80 or (query[2].toInt() and 1)).toByte() // QR and original RD
        this[3] = 0x82.toByte() // RA, SERVFAIL; no invented address
        for (index in 6..11) this[index] = 0 // no answer/authority/additional records
    }

    /** One uncompressed question, standard opcode, bounded wire-format labels. */
    private fun questionEnd(query: ByteArray): Int? {
        if (query.size < 12 || query[2].toInt() and 0xf8 != 0 ||
            query[4].toInt() != 0 || query[5].toInt() != 1) return null
        var index = 12
        while (index < query.size) {
            val size = query[index++].toInt() and 0xff
            if (size == 0) return (index + 4).takeIf { it <= query.size }
            if (size > 63 || index + size >= query.size || index + size > 267) return null
            index += size
        }
        return null
    }

    /** Keep the local listener alive to return SERVFAIL while native stop drains lookups. */
    fun stopResolving() { runCatching { exchange.close() } }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { tcp.close() }
        udp.close()
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        stopResolving()
        workers.shutdownNow()
    }
}
