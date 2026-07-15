package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionManagerHysteriaJsonTest {

    @Test
    fun parseServerLinksFromClientJsonConfig_extractsSingBoxHysteria2Outbound() {
        val json = """
            {
              "dns": {},
              "inbounds": [
                { "type": "tun", "tag": "tun-in" }
              ],
              "outbounds": [
                { "type": "direct", "tag": "direct" },
                {
                  "type": "hysteria2",
                  "tag": "HY EU",
                  "server": "hy.example.com",
                  "server_port": 443,
                  "password": "secret password",
                  "up_mbps": 50,
                  "down_mbps": 100,
                  "tls": {
                    "enabled": true,
                    "server_name": "sni.example.com",
                    "insecure": true,
                    "alpn": ["h3"]
                  },
                  "obfs": {
                    "type": "salamander",
                    "password": "obfs-secret"
                  }
                }
              ]
            }
        """.trimIndent()

        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig(json)

        assertEquals(1, links.size)
        val link = links.single()
        assertTrue(link.startsWith("hy2://secret%20password@hy.example.com:443?"))
        assertTrue(link.contains("sni=sni.example.com"))
        assertTrue(link.contains("insecure=1"))
        assertTrue(link.contains("obfs=salamander"))
        assertTrue(link.contains("obfs-password=obfs-secret"))
        assertTrue(link.contains("upmbps=50"))
        assertTrue(link.contains("downmbps=100"))
        assertTrue(link.endsWith("#HY%20EU"))
    }

    @Test
    fun parseServerLinksFromClientJsonConfig_extractsNestedSingBoxHysteria2OutboundWithPortHopping() {
        val json = """
            {
              "response": {
                "config": {
                  "outbounds": [
                    {
                      "type": "hysteria2",
                      "tag": "HY Hop",
                      "server": "hop.example.com",
                      "server_ports": ["20000:30000"],
                      "hop_interval": "30s",
                      "password": "hop-secret",
                      "tls": {
                        "server_name": "hop-sni.example.com"
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig(json)

        assertEquals(1, links.size)
        val link = links.single()
        assertTrue(link.startsWith("hy2://hop-secret@hop.example.com:20000?"))
        assertTrue(link.contains("mport=20000%3A30000"))
        assertTrue(link.contains("hopInterval=30s"))
        assertTrue(link.endsWith("#HY%20Hop"))
    }

    @Test
    fun parseServerLinksFromClientJsonConfig_extractsXrayHysteriaOutbound() {
        val json = """
            {
              "inbounds": [],
              "outbounds": [
                {
                  "tag": "proxy-hy",
                  "protocol": "hysteria",
                  "settings": {
                    "version": 2,
                    "address": "xray-hy.example.com",
                    "port": 8443
                  },
                  "streamSettings": {
                    "network": "hysteria",
                    "security": "tls",
                    "hysteriaSettings": {
                      "version": 2,
                      "auth": "xray-secret"
                    },
                    "tlsSettings": {
                      "serverName": "xray-sni.example.com",
                      "allowInsecure": false,
                      "alpn": ["h3"]
                    },
                    "finalmask": {
                      "udp": [
                        {
                          "type": "salamander",
                          "settings": { "password": "mask-secret" }
                        }
                      ],
                      "quicParams": {
                        "brutalUp": "75 mbps",
                        "brutalDown": "120 mbps",
                        "udpHop": {
                          "ports": "20000-30000",
                          "interval": "10"
                        }
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig(json)

        assertEquals(1, links.size)
        val link = links.single()
        assertTrue(link.startsWith("hy2://xray-secret@xray-hy.example.com:8443?"))
        assertTrue(link.contains("sni=xray-sni.example.com"))
        assertTrue(link.contains("insecure=0"))
        assertTrue(link.contains("obfs=salamander"))
        assertTrue(link.contains("obfs-password=mask-secret"))
        assertTrue(link.contains("mport=20000-30000"))
        assertTrue(link.contains("hopInterval=10"))
        assertTrue(link.endsWith("#proxy-hy"))
    }

    @Test
    fun detectPrimaryProxyProtocolFromJsonConfig_detectsXrayHysteria2Inbound() {
        val json = """
            {
              "log": { "loglevel": "none" },
              "inbounds": [
                {
                  "tag": "HYSTERIA-BBR",
                  "port": 443,
                  "listen": "0.0.0.0",
                  "protocol": "hysteria",
                  "settings": {
                    "clients": [],
                    "version": 2
                  },
                  "streamSettings": {
                    "network": "hysteria",
                    "security": "tls",
                    "finalmask": {
                      "quicParams": { "debug": false, "congestion": "bbr" }
                    },
                    "tlsSettings": {
                      "alpn": ["h3"],
                      "certificates": [
                        {
                          "keyFile": "/etc/letsencrypt/live/your-domain.com/privkey.pem",
                          "certificateFile": "/etc/letsencrypt/live/your-domain.com/fullchain.pem"
                        }
                      ]
                    },
                    "hysteriaSettings": { "version": 2 }
                  }
                }
              ],
              "outbounds": [
                { "tag": "direct", "protocol": "freedom" },
                { "tag": "block", "protocol": "blackhole" }
              ]
            }
        """.trimIndent()

        val protocol = SubscriptionManager.detectPrimaryProxyProtocolFromJsonConfig(json)

        assertEquals("hysteria2", protocol)
    }

    @Test
    fun parseServerLinksFromClientJsonConfig_extractsXrayHysteriaInboundWhenHostIsPublic() {
        val json = """
            {
              "inbounds": [
                {
                  "tag": "HY Inbound",
                  "protocol": "hysteria",
                  "host": "inbound-hy.example.com",
                  "port": 443,
                  "settings": {
                    "version": 2,
                    "clients": [
                      { "password": "client-secret" }
                    ]
                  },
                  "streamSettings": {
                    "network": "hysteria",
                    "security": "tls",
                    "hysteriaSettings": {
                      "version": 2
                    },
                    "tlsSettings": {
                      "serverName": "inbound-sni.example.com",
                      "alpn": ["h3"]
                    }
                  }
                }
              ],
              "outbounds": [
                { "tag": "direct", "protocol": "freedom" }
              ]
            }
        """.trimIndent()

        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig(json)

        assertEquals(1, links.size)
        val link = links.single()
        assertTrue(link.startsWith("hy2://client-secret@inbound-hy.example.com:443?"))
        assertTrue(link.contains("sni=inbound-sni.example.com"))
        assertTrue(link.endsWith("#HY%20Inbound"))
    }

    @Test
    fun parseServerLinksFromClientJsonConfig_prefersNamedHysteriaDuplicateOverProxyTag() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "hysteria2",
                  "tag": "proxy",
                  "server": "pl.example.com",
                  "server_port": 443,
                  "password": "secret",
                  "tls": { "server_name": "pl.example.com" }
                },
                {
                  "type": "hysteria2",
                  "tag": "🇵🇱 Польша | HYSTERIA",
                  "server": "pl.example.com",
                  "server_port": 443,
                  "password": "secret",
                  "tls": { "server_name": "pl.example.com" }
                }
              ]
            }
        """.trimIndent()

        val links = SubscriptionManager.parseServerLinksFromClientJsonConfig(json)

        assertEquals(1, links.size)
        assertTrue(links.single().endsWith("#%F0%9F%87%B5%F0%9F%87%B1%20%D0%9F%D0%BE%D0%BB%D1%8C%D1%88%D0%B0%20%7C%20HYSTERIA"))
    }

    @Test
    fun isGenericServerName_flagsTechnicalTagsButKeepsRealNames() {
        // Технические теги outbound'ов и заглушки — считаются «без имени».
        for (generic in listOf(
            "proxy", "proxy-1", "proxy_2", "proxy 3", "PROXY-10",
            "out", "outbound-1", "node5", "server", "Hysteria2", "Hysteria2 hy.example.com",
            "", "   "
        )) {
            assertTrue("expected generic: '$generic'", SubscriptionManager.isGenericServerName(generic))
        }
        // Настоящие названия — не считаются генериком.
        for (real in listOf(
            "Германия", "🇩🇪 Германия", "Germany Hysteria2", "DE-Frankfurt-1",
            "Нидерланды", "proxima", "node-juliet"
        )) {
            assertFalse("expected real: '$real'", SubscriptionManager.isGenericServerName(real))
        }
    }

    @Test
    fun parseServerLinksFromApiJson_extractsClashYamlHysteria2FromJsonString() {
        val yaml = """
            proxies:
              - name: HY YAML
                type: hysteria2
                server: yaml.example.com
                port: 443
                password: yaml-secret
                sni: yaml-sni.example.com
                skip-cert-verify: true
                obfs: salamander
                obfs-password: yaml-obfs
        """.trimIndent()
        val json = org.json.JSONObject()
            .put("data", org.json.JSONObject().put("config", yaml))
            .toString()

        val links = SubscriptionManager.parseServerLinksFromApiJson(json)

        assertEquals(1, links?.size)
        val link = links!!.single()
        assertTrue(link.startsWith("hy2://yaml-secret@yaml.example.com:443?"))
        assertTrue(link.contains("sni=yaml-sni.example.com"))
        assertTrue(link.contains("insecure=1"))
        assertTrue(link.contains("obfs=salamander"))
        assertTrue(link.contains("obfs-password=yaml-obfs"))
        assertTrue(link.endsWith("#HY%20YAML"))
    }
}
