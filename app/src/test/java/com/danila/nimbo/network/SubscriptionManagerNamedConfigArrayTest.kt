package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * Подписки Happ-формата (TITAN VPS и подобные) отдают корневой JSON-массив
 * полноценных клиентских конфигов: один элемент = один сервер в списке, его имя
 * лежит в "remarks", а outbounds внутри — участники балансировщика.
 *
 * Раньше разбор разворачивал все outbounds в плоский список, поэтому вместо
 * «🇫🇮 Финляндия 1» пользователь видел технические теги bal / bal-2 / proxy,
 * а серверов оказывалось втрое больше, чем на самом деле.
 */
class SubscriptionManagerNamedConfigArrayTest {

    private fun fragmentName(link: String): String =
        URLDecoder.decode(link.substringAfterLast('#', ""), "UTF-8")

    private fun vlessOutbound(tag: String, address: String) = """
        {
          "tag": "$tag",
          "protocol": "vless",
          "settings": { "vnext": [ { "address": "$address", "port": 443,
            "users": [ { "id": "uuid-1", "encryption": "none", "flow": "" } ] } ] },
          "streamSettings": {
            "network": "xhttp",
            "xhttpSettings": { "mode": "auto", "host": "", "path": "/" },
            "security": "reality",
            "realitySettings": {
              "serverName": "www.intel.com",
              "publicKey": "PUBKEY-$tag",
              "shortId": "5e29",
              "fingerprint": "firefox"
            }
          }
        }
    """.trimIndent()

    private val balancerConfig = """
        {
          "remarks": "🇫🇮 Финляндия 1 WiFi LTE",
          "routing": { "balancers": [ { "tag": "BAL-LTE", "selector": [ "bal-" ] } ] },
          "outbounds": [
            ${vlessOutbound("bal", "45.137.69.248")},
            ${vlessOutbound("bal-2", "45.137.69.249")},
            ${vlessOutbound("bal-3", "45.137.69.250")},
            { "tag": "direct", "protocol": "freedom" },
            { "tag": "block", "protocol": "blackhole" }
          ]
        }
    """.trimIndent()

    private val trojanConfig = """
        {
          "remarks": "🇸🇪 Швеция 2 WiFi LTE",
          "outbounds": [
            {
              "tag": "proxy",
              "protocol": "trojan",
              "settings": { "servers": [ { "address": "89.22.238.19", "port": 5443,
                "password": "trojan-pass" } ] },
              "streamSettings": {
                "network": "grpc",
                "grpcSettings": { "serviceName": "VLGRPC", "authority": "", "mode": false },
                "security": "reality",
                "realitySettings": {
                  "serverName": "www.amd.com",
                  "publicKey": "TROJAN-PUBKEY",
                  "shortId": "e2",
                  "fingerprint": "edge"
                }
              }
            },
            { "tag": "direct", "protocol": "freedom" }
          ]
        }
    """.trimIndent()

    private val singleVlessConfig = """
        {
          "remarks": "🇺🇸 США WiFi",
          "outbounds": [
            ${vlessOutbound("proxy", "1.2.3.4")},
            { "tag": "direct", "protocol": "freedom" }
          ]
        }
    """.trimIndent()

    @Test
    fun namedConfigArray_yieldsOneServerPerConfigNamedByRemarks() {
        val payload = "[$balancerConfig,$trojanConfig,$singleVlessConfig]"

        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig(payload)

        assertEquals(3, links.size)
        assertEquals(
            listOf(
                "🇫🇮 Финляндия 1 WiFi LTE",
                "🇸🇪 Швеция 2 WiFi LTE",
                "🇺🇸 США WiFi"
            ),
            links.map { fragmentName(it) }
        )
        // Ни одного технического тега балансировщика в именах.
        assertTrue(links.none { fragmentName(it).startsWith("bal") || fragmentName(it) == "proxy" })
    }

    @Test
    fun balancerConfig_collapsesToItsFirstMember() {
        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig("[$balancerConfig]")

        assertEquals(1, links.size)
        assertTrue(links[0].contains("45.137.69.248:443"))
    }

    @Test
    fun realityOutbound_keepsPublicKeyAndShortIdAndSni() {
        val link = SubscriptionManager.parseServerLinksFromClientJsonConfig("[$singleVlessConfig]").single()

        assertTrue("pbk отсутствует: $link", link.contains("pbk=PUBKEY-proxy"))
        assertTrue("sid отсутствует: $link", link.contains("sid=5e29"))
        assertTrue("sni отсутствует: $link", link.contains("sni=www.intel.com"))
        assertTrue("fp отсутствует: $link", link.contains("fp=firefox"))
        assertTrue("security=reality отсутствует: $link", link.contains("security=reality"))
    }

    @Test
    fun trojanOutbound_producesLinkWithGrpcAndRealityParams() {
        val link = SubscriptionManager.parseServerLinksFromClientJsonConfig("[$trojanConfig]").single()

        assertTrue("не trojan-ссылка: $link", link.startsWith("trojan://trojan-pass@89.22.238.19:5443"))
        assertTrue("serviceName отсутствует: $link", link.contains("serviceName=VLGRPC"))
        assertTrue("pbk отсутствует: $link", link.contains("pbk=TROJAN-PUBKEY"))
        assertTrue("type=grpc отсутствует: $link", link.contains("type=grpc"))
    }

    @Test
    fun unnamedConfigArray_keepsLegacyFlattening() {
        // Конфиги без remarks (старый формат nebulaguard) должны разбираться как раньше:
        // каждый outbound — отдельный сервер.
        val unnamed = """
            [
              { "outbounds": [ ${vlessOutbound("proxy", "10.0.0.1")}, ${vlessOutbound("proxy-2", "10.0.0.2")} ] }
            ]
        """.trimIndent()

        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig(unnamed)

        assertEquals(2, links.size)
        assertEquals(listOf("proxy", "proxy-2"), links.map { fragmentName(it) })
    }

    private fun hysteriaOutbound(tag: String, address: String, port: Int) = """
        {
          "tag": "$tag",
          "protocol": "hysteria",
          "settings": { "address": "$address", "port": $port, "version": 2 },
          "streamSettings": {
            "network": "hysteria",
            "hysteriaSettings": { "version": 2, "auth": "auth-key" },
            "security": "tls",
            "tlsSettings": { "serverName": "hy.example.com", "alpn": [ "h3" ] }
          }
        }
    """.trimIndent()

    @Test
    fun namedEntriesSharingOneHysteriaEndpoint_staySeparate() {
        // Реальный случай TITAN VPS: «Обходы LTE», «АВТО» и «Обход 1» — разные
        // пункты подписки, но у всех одна и та же точка входа hysteria.
        // Ключ дедупликации для hysteria игнорирует query, поэтому раньше
        // такие пункты слипались в один и из списка пропадали серверы.
        val payload = """
            [
              { "remarks": "Обходы LTE", "outbounds": [ ${hysteriaOutbound("proxy", "185.22.234.59", 20112)} ] },
              { "remarks": "АВТО",       "outbounds": [ ${hysteriaOutbound("obal", "185.22.234.59", 20112)} ] },
              { "remarks": "Обход 1",    "outbounds": [ ${hysteriaOutbound("proxy", "185.22.234.59", 20112)} ] }
            ]
        """.trimIndent()

        val links = SubscriptionManager.parseServerLinksFromSubscriptionText(payload)

        assertEquals(listOf("Обходы LTE", "АВТО", "Обход 1"), links.map { fragmentName(it) })
    }

    @Test
    fun unnamedHysteriaDuplicate_stillMergesIntoTheNamedOne() {
        // Обратная сторона: один и тот же узел, пришедший без имени, не должен
        // дублировать уже найденный именованный.
        val payload = """
            [
              { "remarks": "🇫🇮 Финляндия 4 GAME", "outbounds": [ ${hysteriaOutbound("proxy", "87.242.100.46", 20111)} ] },
              { "outbounds": [ ${hysteriaOutbound("proxy", "87.242.100.46", 20111)} ] }
            ]
        """.trimIndent()

        val links = SubscriptionManager.parseServerLinksFromSubscriptionText(payload)

        assertEquals(listOf("🇫🇮 Финляндия 4 GAME"), links.map { fragmentName(it) })
    }

    @Test
    fun singleConfigObject_isUnaffected() {
        // Одиночный объект-конфиг (не массив) продолжает разворачиваться по outbounds.
        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig(balancerConfig)

        assertEquals(3, links.size)
        assertEquals(listOf("bal", "bal-2", "bal-3"), links.map { fragmentName(it) })
    }
}
