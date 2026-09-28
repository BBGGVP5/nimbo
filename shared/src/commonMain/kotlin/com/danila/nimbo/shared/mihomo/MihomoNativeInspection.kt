package com.danila.nimbo.shared.mihomo

import kotlinx.serialization.json.*

/** An inspect response is a lossless import projection, never runtime readiness. */
sealed interface MihomoInspectionResult {
    data class Inspected(val document: MihomoDocument, val sourceSHA256: String) : MihomoInspectionResult {
        override fun toString(): String = "MihomoInspectionResult.Inspected(configuration=<redacted>)"
    }
    /** Codes only: native parse errors can include passwords or provider URLs. */
    data class Rejected(val code: String) : MihomoInspectionResult
}

/**
 * Boundary for wire API 1. Platform code hashes the exact submitted UTF-8 document before invoking
 * native inspect; both that digest AND the echoed original must match. No Kotlin YAML parser,
 * regexp expansion, provider fetch or transformation into a runnable config happens here.
 */
object MihomoNativeInspection {
    private const val maxSourceBytes = 4 * 1024 * 1024
    private const val maxResponseChars = 16 * 1024 * 1024
    private const val maxGraphEntries = 20_000
    private val digestPattern = Regex("[0-9a-f]{64}")
    private val safeCodePattern = Regex("[A-Z][A-Z0-9_]{0,63}")
    private val json = Json { isLenient = false }

    fun decode(response: String, requestId: String, originalText: String, expectedSHA256: String): MihomoInspectionResult {
        if (originalText.length > maxSourceBytes || originalText.encodeToByteArray().size > maxSourceBytes ||
            response.length > maxResponseChars || requestId.isBlank() || !digestPattern.matches(expectedSHA256)) {
            return MihomoInspectionResult.Rejected("INSPECTION_INPUT_INVALID")
        }
        return try {
            val root = json.parseToJsonElement(response).objectValue()
            check(root.number("apiVersion") == 1L)
            check(root.string("requestId") == requestId)
            check(root.number("generation") >= 0)
            if (!root.boolean("success")) {
                val code = root.getValue("error").objectValue().string("code")
                MihomoInspectionResult.Rejected(if (safeCodePattern.matches(code)) code else "NATIVE_INSPECTION_FAILED")
            } else {
                val data = root.getValue("data").objectValue()
                check(data.string("originalYAML") == originalText)
                val hash = data.string("sourceSHA256")
                check(hash == expectedSHA256)
                val graph = data.getValue("declaredGraph").objectValue()
                val proxies = graph.array("proxies").map { value ->
                    val proxy = value.objectValue()
                    MihomoProxy(proxy.string("name"), proxy.string("type"))
                }
                val groups = graph.array("groups").map { value ->
                    val group = value.objectValue()
                    MihomoGroup(
                        name = group.string("name"), type = group.string("type"),
                        proxies = group.strings("proxies"), use = group.strings("use"),
                        includeAll = group.optionalBoolean("include-all"),
                        includeAllProxies = group.optionalBoolean("include-all-proxies"),
                        includeAllProviders = group.optionalBoolean("include-all-providers"),
                        filter = group.optionalString("filter"), excludeFilter = group.optionalString("exclude-filter"),
                        excludeType = group.optionalString("exclude-type"), emptyFallback = group.optionalString("empty-fallback"),
                        hidden = group.optionalBoolean("hidden"), icon = group.optionalString("icon")
                    )
                }
                val providers = graph.getValue("providers").objectValue().also { check(it.size <= maxGraphEntries) }
                    .map { (name, raw) -> MihomoProxyProvider(name, raw.objectValue().string("type")) }
                check(proxies.size + groups.size + providers.size <= maxGraphEntries)
                val issues = data.array("strictIssues").map { value ->
                    val issue = value.objectValue()
                    val code = issue.string("code")
                    check(safeCodePattern.matches(code))
                    // A message may quote the raw secret. Keep the diagnostic code + structural path.
                    MihomoUnsupportedFeature(issue.optionalString("path")?.ifEmpty { "$" } ?: "$", code)
                }
                MihomoInspectionResult.Inspected(MihomoDocument(originalText, MihomoGraph(proxies, groups, providers), issues), hash)
            }
        } catch (_: Exception) {
            MihomoInspectionResult.Rejected("INSPECTION_RESPONSE_INVALID")
        }
    }

    private fun JsonElement.objectValue(): JsonObject = this as? JsonObject ?: error("object")
    private fun JsonElement.stringValue(): String = (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("string")
    private fun JsonObject.string(key: String): String = getValue(key).stringValue()
    private fun JsonObject.number(key: String): Long = (getValue(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: error("integer")
    private fun JsonObject.boolean(key: String): Boolean = (getValue(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: error("boolean")
    private fun JsonObject.optionalBoolean(key: String): Boolean = if (containsKey(key)) boolean(key) else false
    private fun JsonObject.optionalString(key: String): String? = if (containsKey(key)) string(key) else null
    private fun JsonObject.array(key: String): JsonArray = (getValue(key) as? JsonArray)?.also { check(it.size <= maxGraphEntries) } ?: error("array")
    private fun JsonObject.strings(key: String): List<String> = if (containsKey(key)) array(key).map { it.stringValue() } else emptyList()
}
