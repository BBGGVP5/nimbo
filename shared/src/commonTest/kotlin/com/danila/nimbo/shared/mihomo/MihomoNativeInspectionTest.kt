package com.danila.nimbo.shared.mihomo

import kotlinx.serialization.json.*
import kotlin.test.*

class MihomoNativeInspectionTest {
    private val yaml = "# preserve comments and unknown options\nproxies: []\n"
    private val hash = "a".repeat(64)
    private fun fixture(mutator: (MutableMap<String, JsonElement>) -> Unit = {}): String {
        val data = buildJsonObject {
            put("originalYAML", yaml); put("sourceSHA256", hash)
            put("declaredGraph", buildJsonObject {
                put("proxies", buildJsonArray { add(buildJsonObject { put("name", "Tokyo"); put("type", "vless"); put("password", "never-log-me") }) })
                put("groups", buildJsonArray { add(buildJsonObject {
                    put("name", "Automatic"); put("type", "url-test"); put("include-all-providers", true)
                    put("filter", "(?<=region)東京"); put("exclude-filter", "expired"); put("exclude-type", "direct")
                    put("empty-fallback", "DIRECT"); put("future-field", "unchanged in original")
                    put("proxies", buildJsonArray { add("Tokyo") }); put("use", buildJsonArray { add("Remote") })
                }) })
                put("providers", buildJsonObject { put("Remote", buildJsonObject { put("type", "http"); put("url", "https://example.invalid/secret") }) })
            })
            put("strictIssues", buildJsonArray { add(buildJsonObject {
                put("code", "UNSUPPORTED_FIELD"); put("path", "future-field"); put("message", "private credentials must not leak")
            }) })
        }
        val root = mutableMapOf<String, JsonElement>("apiVersion" to JsonPrimitive(1), "requestId" to JsonPrimitive("inspect-1"),
            "success" to JsonPrimitive(true), "generation" to JsonPrimitive(0), "data" to data)
        mutator(root)
        return JsonObject(root).toString()
    }
    private fun decode(response: String) = MihomoNativeInspection.decode(response, "inspect-1", yaml, hash)

    @Test fun preservesOriginalAndDynamicGraphWithoutReconstructingConfig() {
        val result = assertIs<MihomoInspectionResult.Inspected>(decode(fixture()))
        assertEquals(yaml, result.document.originalText)
        val graph = result.document.graph!!
        assertEquals("vless", graph.proxies.single().type)
        val group = graph.groups.single()
        assertEquals("url-test", group.type)
        assertTrue(group.includesAllProviders)
        assertEquals(listOf("Remote"), group.use)
        assertEquals("(?<=region)東京", group.filter)
        assertEquals("direct", group.excludeType)
        assertEquals("http", graph.providers.single().type)
        assertEquals("UNSUPPORTED_FIELD", result.document.unsupportedFeatures.single().reason)
        assertFalse(result.toString().contains("never-log-me"))
        assertFalse(result.document.toString().contains(yaml))
    }
    @Test fun rejectsStaleVersionSourceDigestAndRequest() {
        val fields = listOf(
            "apiVersion" to JsonPrimitive(2), "apiVersion" to JsonPrimitive("1"),
            "requestId" to JsonPrimitive("stale"), "generation" to JsonPrimitive(-1),
            "success" to JsonPrimitive("true"))
        fields.forEach { (key, value) -> assertIs<MihomoInspectionResult.Rejected>(decode(fixture { it[key] = value })) }
        for ((key, value) in listOf("sourceSHA256" to "b".repeat(64), "originalYAML" to yaml.trim())) {
            assertIs<MihomoInspectionResult.Rejected>(decode(fixture {
                val data = it.getValue("data").jsonObject.toMutableMap(); data[key] = JsonPrimitive(value); it["data"] = JsonObject(data)
            }))
        }
    }
    @Test fun rejectsInvalidGraphInsteadOfCoercingAndSilentlyLosingEntries() {
        assertIs<MihomoInspectionResult.Rejected>(decode(fixture {
            val data = it.getValue("data").jsonObject.toMutableMap()
            data["declaredGraph"] = buildJsonObject { put("proxies", JsonNull); put("groups", JsonArray(emptyList())); put("providers", JsonObject(emptyMap())) }
            it["data"] = JsonObject(data)
        }))
        assertIs<MihomoInspectionResult.Rejected>(decode("not json secret material"))
    }
    @Test fun returnsOnlySafeNativeErrorCodeNotCredentialBearingMessage() {
        val failure = fixture {
            it["success"] = JsonPrimitive(false)
            it["error"] = buildJsonObject { put("code", "CONFIG_INVALID"); put("message", "password=hidden") }
        }
        assertEquals(MihomoInspectionResult.Rejected("CONFIG_INVALID"), decode(failure))
        assertFalse(decode(failure).toString().contains("hidden"))
        assertEquals(MihomoInspectionResult.Rejected("INSPECTION_INPUT_INVALID"),
            MihomoNativeInspection.decode(fixture(), "", yaml, hash))
    }

    @Test fun rootIssueMayOmitItsOptionalPathWithoutLosingTheDocument() {
        val wire = fixture {
            val data = it.getValue("data").jsonObject.toMutableMap()
            data["strictIssues"] = buildJsonArray { add(buildJsonObject {
                put("code", "UNSUPPORTED_CONFIG"); put("message", "native text is not UI-safe")
            }) }
            it["data"] = JsonObject(data)
        }
        val result = assertIs<MihomoInspectionResult.Inspected>(decode(wire))
        assertEquals("$", result.document.unsupportedFeatures.single().path)
        assertEquals(yaml, result.document.originalText)
    }
}
