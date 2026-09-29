package com.danila.nimbo.mihomo

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.danila.nimbo.ui.screens.SubscriptionProfile
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class MihomoProtocolTest {
    @Test fun requestsPreserveOriginalYamlAndEscapeIdentifiers() {
        val yaml = "# comment\r\nmode: global\r\n"
        val request = MihomoProtocol.request("inspect", yaml, requestId = "quote\"id")
        assertEquals(yaml, request["yaml"].asString)
        assertEquals("quote\"id", request["requestId"].asString)
        assertEquals(1, request["apiVersion"].asInt)
    }

    @Test fun fieldsCannotOverrideRequestIdentityOrOperation() {
        assertThrows(IllegalArgumentException::class.java) {
            MihomoProtocol.request("status", fields = JsonObject().apply { addProperty("operation", "start") })
        }
    }

    @Test fun rejectsWrongVersionIdAndGeneration() {
        listOf(
            """{"apiVersion":2,"requestId":"id","success":true,"generation":1}""",
            """{"apiVersion":1,"requestId":"other","success":true,"generation":1}""",
            """{"apiVersion":1,"requestId":"id","success":true}""",
            """{"apiVersion":1,"requestId":"id","success":true,"generation":1.1}""",
            """{"apiVersion":1,"requestId":"id","success":true,"generation":-1}"""
        ).forEach { raw -> assertThrows(MihomoException::class.java) { MihomoProtocol.decode(raw, "id") } }
    }

    @Test fun returnsDataAndGenerationTogether() {
        val reply = MihomoProtocol.decode("""{"apiVersion":1,"requestId":"id","success":true,"generation":4,"data":{"state":"running"}}""", "id")
        assertEquals(4L, reply.generation)
        assertEquals("running", reply.data["state"].asString)
    }

    @Test fun nativeMessagesCannotExposeSecrets() {
        val error = assertThrows(MihomoException::class.java) {
            MihomoProtocol.decode("""{"apiVersion":1,"requestId":"id","success":false,"generation":0,"error":{"code":"INVALID_YAML","message":"password: secret"}}""", "id")
        }
        assertEquals("Mihomo: INVALID_YAML", error.message)
    }

    @Test fun rejectsOversizedUtf8AndUnpairedSurrogates() {
        assertThrows(MihomoException::class.java) { MihomoProtocol.checkSource("я".repeat(MihomoProtocol.MAX_SOURCE_BYTES / 2 + 1)) }
        assertThrows(MihomoException::class.java) { MihomoProtocol.checkSource("broken\uD800") }
    }

    @Test fun profileBackupRoundTripKeepsSourceAndNativeIdentity() {
        val yaml = "# full source\r\nmode: global\r\nproxies: []\r\n"
        val profile = MihomoProfiles.project("Test", yaml, id = "fixed-id")
        val restored = Gson().fromJson(Gson().toJson(profile), SubscriptionProfile::class.java)
        assertEquals(yaml, restored.rawConfig)
        assertEquals("mihomo", restored.configType)
        assertEquals("mihomo://fixed-id", restored.url)
        assertEquals("mihomo", restored.servers.single().protocol)
        assertEquals(restored.url, restored.servers.single().profileUrl)
        assertFalse(com.danila.nimbo.utils.isNoticePlaceholderServer(restored.servers.single()))
        assertTrue(com.danila.nimbo.utils.isNoticePlaceholderServer(restored.servers.single().copy(protocol = "vless")))
        val replacement = MihomoProfiles.project("Changed", yaml + "# updated", restored)
        assertEquals(restored.url, replacement.url)
        assertEquals(restored.servers.single().uuid, replacement.servers.single().uuid)
        assertNotEquals(MihomoProtocol.sourceHash(yaml), MihomoProtocol.sourceHash(replacement.rawConfig!!))
    }

    @Test fun subscriptionReaderRejectsOversizedAndInvalidUtf8WithoutPersisting() {
        val original = "# exact YAML\r\nmode: rule\r\n"
        assertEquals(original, MihomoSubscriptionFetcher.decode(ByteArrayInputStream(original.toByteArray())))
        assertThrows(MihomoException::class.java) {
            MihomoSubscriptionFetcher.decode(ByteArrayInputStream(ByteArray(MihomoProtocol.MAX_SOURCE_BYTES + 1) { 'x'.code.toByte() }))
        }
        assertThrows(MihomoException::class.java) {
            MihomoSubscriptionFetcher.decode(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
        val profile = MihomoProfiles.project("Remote", original, id = "source-1")
            .copy(mihomoSourceUrl = "https://example.test/sub?token=private")
        val restored = Gson().fromJson(Gson().toJson(profile), SubscriptionProfile::class.java)
        assertEquals(original, restored.rawConfig)
        assertEquals(profile.mihomoSourceUrl, restored.mihomoSourceUrl)
        assertEquals("mihomo://source-1", restored.url)
    }

    @Test fun subscriptionUrlRequiresHttpsWithoutEmbeddedCredentialsOrFragments() {
        assertEquals("https://example.test/sub?token=private",
            MihomoSubscriptionFetcher.checkedUrl("https://example.test/sub?token=private").toString())
        for (source in listOf("http://example.test/sub", "https://user:pass@example.test/sub",
            "https://example.test/sub#fragment", "file:///sdcard/sub.yaml", "not a URL")) {
            val error = assertThrows(MihomoException::class.java) {
                MihomoSubscriptionFetcher.checkedUrl(source)
            }
            assertEquals("INVALID_SUBSCRIPTION_URL", error.code)
        }
    }
}
