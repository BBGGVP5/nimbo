package com.danila.nimbo.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danila.nimbo.vpn.LocalProxyConfig
import com.danila.nimbo.vpn.VpnManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Requires a device with a physical network; exercises real Binder + isolated libXray + GET. */
@RunWith(AndroidJUnit4::class)
class NimboNodePingProcessTest {
    @Test fun twoIndependentServerRoutesLeaveMainVpnStateUntouched() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalState = VpnManager.state.value
        val originalServer = VpnManager.connectedServer.value
        val originalSelection = VpnManager.selectedServer
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { proxy ->
            proxy.soTimeout = 15_000
            val fixture = executor.submit {
                repeat(2) { index ->
                    proxy.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val reader = socket.getInputStream().bufferedReader()
                        val first = reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) { /* consume proxy request headers */ }
                        if (first.startsWith("CONNECT ")) {
                            socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                            socket.getOutputStream().flush()
                            requests += reader.readLine()
                            while (!reader.readLine().isNullOrEmpty()) { }
                        } else requests += first
                        val status = if (index == 0) "204 No Content" else "503 Unavailable"
                        socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.getOutputStream().flush()
                    }
                }
            }
            fun node(tag: String): String = JSONObject()
                .put("outbounds", JSONArray()
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                    .put(JSONObject().put("tag", tag).put("protocol", "http").put("settings", JSONObject()
                        .put("servers", JSONArray().put(JSONObject().put("address", "127.0.0.1").put("port", proxy.localPort))))))
                .put("routing", JSONObject().put("rules", LocalProxyConfig.prependRoute(JSONArray(), tag, null))).toString()
            try {
                assertTrue(NimboNodePing.measure(context, node("first-node"), "http://never-direct.invalid/probe", 4000) >= 0)
                assertEquals(-1, NimboNodePing.measure(context, node("second-node"), "http://never-direct.invalid/probe", 4000))
                fixture.get(15, TimeUnit.SECONDS)
                assertEquals(2, requests.size)
                assertTrue(requests.all { it.startsWith("GET ") })
                assertEquals(originalState, VpnManager.state.value)
                assertSame(originalServer, VpnManager.connectedServer.value)
                assertSame(originalSelection, VpnManager.selectedServer)
            } finally { executor.shutdownNow() }
        }
    }
}
