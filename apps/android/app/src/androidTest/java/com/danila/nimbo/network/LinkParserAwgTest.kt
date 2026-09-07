package com.danila.nimbo.network

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверяет парсинг AmneziaWG/WireGuard: awg:// с base64 INI (формат клиента
 * Amnezia), query-формат панелей и сырые wg-конфиги. Запускается на устройстве,
 * т.к. LinkParser использует android.net.Uri и android.util.Base64.
 */
@RunWith(AndroidJUnit4::class)
class LinkParserAwgTest {

    private fun b64(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun awgIni(): String = """
        [Interface]
        PrivateKey = mF2b9JkQxR5zNpO3sLqWvE7yH4dG8uTc1aIoUjKbVgA=
        Address = 10.66.66.2/32
        DNS = 1.1.1.1, 8.8.8.8
        Jc = 5
        Jmin = 40
        Jmax = 70
        S1 = 7
        S2 = 7
        S3 = 7
        S4 = 7
        H1 = 125369-253694
        H2 = 153696-236969
        H3 = 125398-236963
        H4 = 125366-253966
        I1 = 100
        I2 = 1000
        I3 = 100
        I4 = 10000
        I5 = 100
        MTU = 1420

        [Peer]
        PublicKey = uRbS0dJuI9Qy8TpXnC2wVz5kGm7Hb4qEa1fD3sL6cNj=
        PresharedKey = xYz7wQ2nRm4pLv9sTf8bHk5Jc1dG0aUe6iMoP3tSs=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = vpn.example.com:51820
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun awgLink_withBase64Ini_parsesObfuscationAndPeer() {
        val link = "awg://${b64(awgIni())}#MyAWG%20Server"

        val server = LinkParser.parse(link)

        assertEquals("awg", server.protocol)
        assertEquals("MyAWG Server", server.name)
        assertEquals("vpn.example.com", server.host)
        assertEquals(51820, server.port)
        assertTrue(server.wgPrivateKey!!.startsWith("mF2b9JkQ"))
        assertTrue(server.wgPublicKey!!.startsWith("uRbS0dJu"))
        assertTrue(server.wgPresharedKey!!.isNotEmpty())
        assertEquals("10.66.66.2/32", server.wgAddress)
        assertEquals("0.0.0.0/0, ::/0", server.wgAllowedIps)
        assertEquals(5, server.awgJc)
        assertEquals(40, server.awgJmin)
        assertEquals(70, server.awgJmax)
        assertEquals(7, server.awgS1)
        assertEquals(7, server.awgS4)
        assertEquals("125369-253694", server.awgH1)
        assertEquals("10000", server.awgI4)
        assertEquals(1420, server.wgMtu)
        assertEquals(25, server.wgKeepAlive)
    }

    @Test
    fun awgLink_withQueryParams_parsesPanelFormat() {
        val link = "awg://example.net:443?public_key=AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWw==" +
            "&private_key=ZzYyXxWwVvUuTtSsRrQqPpOoNnMmLlKkJjIiHhGgFfEeDdCcBbAa==" +
            "&jc=3&jmin=50&jmax=1000&s1=2&s2=2&s3=2&s4=2" +
            "&h1=1-2&h2=3-4&h3=5-6&h4=7-8&i1=100&i2=200&i3=300&i4=400&i5=500" +
            "&name=AWG%20Panel%20Node"

        val server = LinkParser.parse(link)

        assertEquals("awg", server.protocol)
        assertEquals("AWG Panel Node", server.name)
        assertEquals("example.net", server.host)
        assertEquals(443, server.port)
        assertEquals(3, server.awgJc)
        assertEquals("1-2", server.awgH1)
        assertEquals(500, server.awgI5)
    }

    @Test
    fun wireguardLink_withQueryParams_parsesNameAndKeys() {
        val link = "wireguard://Finland%20WG?private_key=aaBBccDDeeFFggHHiiJJkkLLmmNN=ooPPqqRRssTTuuVV" +
            "&public_key=wwXXyyZZ=00&&endpoint=77.88.55.33:51820&address=10.0.0.2/32" +
            "&dns=1.1.1.1&mtu=1420&allowed_ips=0.0.0.0/0&keepalive=25"

        val server = LinkParser.parse(link)

        assertEquals("wireguard", server.protocol)
        assertEquals("Finland WG", server.name)
        assertEquals("77.88.55.33", server.host)
        assertEquals(51820, server.port)
        assertEquals("10.0.0.2/32", server.wgAddress)
        assertEquals("1.1.1.1", server.wgDns)
        assertEquals(1420, server.wgMtu)
        assertEquals(25, server.wgKeepAlive)
        assertNull(server.awgJc)
    }

    @Test
    fun plainWgIni_parsesAsWireGuardServer() {
        val server = LinkParser.parse(
            """
            [Interface]
            PrivateKey = AAAABBBBCCCCDDDDEEEEFFFF000011112222333344445555666677778888
            Address = 10.8.0.2/24
            DNS = 1.1.1.1

            [Peer]
            PublicKey = 9999AAAABBBBCCCCDDDDEEEEFFFF00001111222233334444555566667777
            Endpoint = wg.example.org:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
            """.trimIndent()
        )

        assertEquals("wireguard", server.protocol)
        assertEquals("wg.example.org", server.host)
        assertEquals(51820, server.port)
        assertTrue(server.wgPrivateKey!!.startsWith("AAAA"))
        assertEquals("10.8.0.2/24", server.wgAddress)
        assertEquals(25, server.wgKeepAlive)
        assertNull(server.awgH1)
    }

    @Test
    fun awgLink_withBase64Ini_andQueryName_usesQueryName() {
        val link = "awg://${b64(awgIni())}?name=Node%20From%20Query"

        val server = LinkParser.parse(link)

        assertEquals("Node From Query", server.name)
        assertEquals("awg", server.protocol)
        assertEquals("vpn.example.com", server.host)
    }
}
