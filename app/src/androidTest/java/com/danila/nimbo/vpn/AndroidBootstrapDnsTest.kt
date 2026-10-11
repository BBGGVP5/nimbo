package com.danila.nimbo.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Uses system DNS only; does not request VPN consent, start TUN or change settings. */
@RunWith(AndroidJUnit4::class)
class AndroidBootstrapDnsTest {
    @Test fun nativeLoopbackAdapterUsesPhysicalAndroidResolver() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.allNetworks.firstOrNull {
            val caps = cm.getNetworkCapabilities(it)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        assumeNotNull("Test requires a physical network", network)
        val query = byteArrayOf(
            0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
            7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1
        ) // example.com A; never uses a user's subscription or credentials
        repeat(2) { // closing/reopening must not leave the previous resolver's worker alive
            BootstrapDnsServer(AndroidBootstrapDns(context, requireNotNull(network))).use { server ->
                DatagramSocket().use { socket ->
                    socket.soTimeout = 8_000
                    socket.send(DatagramPacket(query, query.size, InetAddress.getByName("127.0.0.1"), server.port))
                    val answer = DatagramPacket(ByteArray(65_535), 65_535)
                    socket.receive(answer)
                    assertTrue(answer.length >= 12)
                    assertEquals(0x12, answer.data[0].toInt() and 0xff)
                    assertEquals(0x34, answer.data[1].toInt() and 0xff)
                    assertEquals("System resolver returned a DNS error", 0, answer.data[3].toInt() and 0x0f)
                    assertTrue("System resolver returned no A answer", answer.data[6].toInt() != 0 || answer.data[7].toInt() != 0)
                }
            }
        }
    }
}
