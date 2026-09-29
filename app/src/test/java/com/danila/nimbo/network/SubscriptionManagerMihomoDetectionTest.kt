package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SubscriptionManagerMihomoDetectionTest {
    private val nativeKindFromParsedFixture: (String) -> Boolean = { source ->
        source.lineSequence().any { line ->
            val key = line.substringBefore(':').trim()
            key in setOf("mode", "proxies", "proxy-groups", "proxy-providers", "rule-providers", "dns")
        }
    }

    @Test
    fun nativeGraphClassificationSupportsOldAndNewInspectionSchemas() {
        val legacy = com.google.gson.JsonObject().apply {
            add("declaredGraph", com.google.gson.JsonObject().apply {
                add("proxies", com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject()) })
                add("groups", com.google.gson.JsonArray())
                add("providers", com.google.gson.JsonObject())
            })
        }
        val current = com.google.gson.JsonObject().apply { addProperty("documentKind", "mihomo") }
        val unrelated = com.google.gson.JsonObject().apply {
            add("declaredGraph", com.google.gson.JsonObject().apply {
                add("proxies", com.google.gson.JsonArray())
                add("groups", com.google.gson.JsonArray())
                add("providers", com.google.gson.JsonObject())
            })
        }
        assertTrue(SubscriptionManager.isNativeMihomoInspection(legacy))
        assertTrue(SubscriptionManager.isNativeMihomoInspection(current))
        assertFalse(SubscriptionManager.isNativeMihomoInspection(unrelated))
    }

    @Test
    fun detectsFullYamlAsOneNativeDocument() {
        val yaml = "mode: rule\nproxy-groups:\n  - name: Auto\n    type: select\n    proxies: [DIRECT]\nrules: [MATCH,Auto]\n"
        val result = SubscriptionManager.detectNativeMihomoDocuments(yaml, nativeKindFromParsedFixture)
        assertEquals(1, result.size)
        assertEquals(yaml, result.single().source)
        assertEquals("default", result.single().documentId)
        assertTrue(result.single().sourceHash.isNotBlank())
    }

    @Test
    fun detectsNamedDocumentsInsideConfigArrayWithoutFlattening() {
        val one = "proxy-groups: [{name: A, type: select, proxies: [DIRECT]}]\n"
        val two = "proxy-groups: [{name: B, type: select, proxies: [DIRECT]}]\n"
        val json = "[{\"remarks\":\"Первый\",\"yaml\":${jsonQuote(one)}},{\"name\":\"Второй\",\"config\":${jsonQuote(two)}}]"
        val result = SubscriptionManager.detectNativeMihomoDocuments(json, nativeKindFromParsedFixture)
        assertEquals(listOf("Первый", "Второй"), result.map { it.name })
        assertEquals(listOf(one, two), result.map { it.source })
        assertEquals(listOf("item-0", "item-1"), result.map { it.documentId })
    }

    @Test
    fun detectsBase64AndMultiDocumentYamlWithoutChangingEachDocument() {
        val first = "# one\r\nmode: rule\r\nproxy-groups: [{name: A, type: select, proxies: [DIRECT]}]\r\n"
        val second = "---\r\nmode: global\r\nproxy-groups: [{name: B, type: select, proxies: [DIRECT]}]\r\n"
        val bundled = first + "---\r\n" + second.removePrefix("---\r\n")
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bundled.toByteArray(Charsets.UTF_8))
        val result = SubscriptionManager.detectNativeMihomoDocuments(encoded, nativeKindFromParsedFixture)
        assertEquals(2, result.size)
        assertEquals(first, result[0].source)
        assertEquals(second, result[1].source)
        assertEquals(listOf("document-0", "document-1"), result.map { it.documentId })
    }

    @Test
    fun keepsStableUniqueIdsForMultiDocumentJsonEnvelope() {
        val first = "---\nmode: rule\nproxy-groups: [{name: A, type: select, proxies: [DIRECT]}]\n"
        val second = "---\nmode: global\nproxy-groups: [{name: B, type: select, proxies: [DIRECT]}]\n"
        val envelope = """{"id":"bundle","config":${jsonQuote(first + second)}}"""
        val result = SubscriptionManager.detectNativeMihomoDocuments(envelope, nativeKindFromParsedFixture)
        assertEquals(listOf(first, second), result.map { it.source })
        assertEquals(listOf("bundle-document-0", "bundle-document-1"), result.map { it.documentId })
    }

    @Test
    fun unrelatedYamlAndMalformedUtf8AreNotClassifiedByTextFragments() {
        val unrelated = "# proxy-groups: example only\nname: ordinary\n"
        assertTrue(SubscriptionManager.detectNativeMihomoDocuments(unrelated) { false }.isEmpty())
        assertTrue(SubscriptionManager.detectNativeMihomoDocuments(byteArrayOf(0xC3.toByte(), 0x28)) {
            true
        }.isEmpty())
    }

    private fun jsonQuote(value: String): String =
        org.json.JSONObject.quote(value)
}
