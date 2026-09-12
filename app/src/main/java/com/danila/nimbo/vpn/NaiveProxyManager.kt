package com.danila.nimbo.vpn

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.danila.nimbo.model.Server
import com.danila.nimbo.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/** Runs the official NaiveProxy client and exposes it to Xray over loopback SOCKS. */
object NaiveProxyManager {
    private const val TAG = "NaiveProxyManager"
    private const val START_TIMEOUT_MS = 5_000L

    @Volatile private var process: Process? = null
    @Volatile private var configFile: File? = null

    suspend fun start(context: Context, server: Server): Server = withContext(Dispatchers.IO) {
        require(server.isNaiveProxy()) { "Server is not a NaiveProxy endpoint" }
        stop()

        val binary = File(context.applicationInfo.nativeLibraryDir, "libnaive.so")
        check(binary.isFile) { "Компонент NaiveProxy отсутствует для архитектуры устройства" }

        val localPort = allocateLoopbackPort()
        val runtimeDir = File(context.filesDir, "runtime/naive")
        check(runtimeDir.isDirectory || runtimeDir.mkdirs()) { "Не удалось создать папку NaiveProxy" }
        val config = File(runtimeDir, "naive-config.json")
        val log = File(runtimeDir, "naive.log")
        val configText = buildConfig(server, localPort).toString(2)
        runCatching { config.writeText(configText, Charsets.UTF_8) }
            .onFailure { config.delete() }
            .getOrElse { error ->
                throw IllegalStateException("Не удалось записать конфиг NaiveProxy: ${error.message}", error)
            }
        configFile = config

        val child = runCatching {
            ProcessBuilder(binary.absolutePath, config.absolutePath)
                .directory(runtimeDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                .start()
        }.getOrElse { error ->
            config.delete()
            configFile = null
            throw IllegalStateException("Не удалось запустить NaiveProxy: ${error.message}", error)
        }
        process = child

        val deadline = SystemClock.elapsedRealtime() + START_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!child.isAlive) break
            if (canConnect(localPort)) {
                // Credentials are only needed while the process reads its config.
                config.delete()
                configFile = null
                Logger.i(TAG, "NaiveProxy sidecar is ready on loopback port $localPort")
                return@withContext server.copy(naiveLocalPort = localPort)
            }
            delay(100L)
        }

        val details = runCatching {
            log.takeIf(File::isFile)?.readLines()?.takeLast(8)?.joinToString(" ")
        }.getOrNull().orEmpty().let(::redact)
        stop()
        throw IllegalStateException(
            "NaiveProxy не запустил локальный прокси" + details.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        )
    }

    internal fun buildConfig(server: Server, localPort: Int): JSONObject {
        val username = server.naiveUsername?.takeIf(String::isNotBlank) ?: server.uuid
        val password = server.naivePassword?.takeIf(String::isNotBlank)
            ?: error("NaiveProxy password is missing")
        val scheme = if (server.naiveTransport.equals("quic", true) || server.network.equals("quic", true)) {
            "quic"
        } else {
            "https"
        }
        val host = if (server.host.contains(':') && !server.host.startsWith("[")) "[${server.host}]" else server.host
        val proxy = "$scheme://${Uri.encode(username)}:${Uri.encode(password)}@$host:${server.port}"
        return JSONObject()
            .put("listen", "socks://127.0.0.1:$localPort")
            .put("proxy", proxy)
    }

    @Synchronized
    fun stop() {
        val child = process
        process = null
        if (child != null && child.isAlive) {
            child.destroy()
            runCatching { child.waitFor(500, TimeUnit.MILLISECONDS) }
            if (child.isAlive) child.destroyForcibly()
        }
        configFile?.delete()
        configFile = null
    }

    private fun allocateLoopbackPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use {
        it.localPort
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 150)
        }
        true
    }.getOrDefault(false)

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(https|quic)://[^@\\s]+@"), "$1://***@")
        .take(800)
}
