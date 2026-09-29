package com.danila.nimbo.shared.mihomo

import kotlinx.serialization.Serializable

/**
 * Original UTF-8 configuration text plus an optional inspection projection. This is NOT a YAML
 * parser or a configuration writer. Keep the original bytes separately when importing non-UTF-8
 * documents. Never reconstruct a runnable configuration from [graph].
 *
 * DNS, rules, rule providers, listeners, protocol options, anchors and unknown fields live in
 * [originalText] unchanged. A native inspector must bind the projection to this exact document.
 */
@Serializable
data class MihomoDocument(
    val originalText: String,
    val graph: MihomoGraph? = null,
    /** Unsupported/ignored fields reported by the native strict inspector; never silently remove. */
    val unsupportedFeatures: List<MihomoUnsupportedFeature> = emptyList(),
) {
    override fun toString(): String = "MihomoDocument(configuration=<redacted>)"
}

@Serializable
data class MihomoUnsupportedFeature(val path: String, val reason: String)

/** Declared graph only. Provider contents and effective group membership come from the runtime. */
@Serializable
data class MihomoGraph(
    val proxies: List<MihomoProxy> = emptyList(),
    val groups: List<MihomoGroup> = emptyList(),
    val providers: List<MihomoProxyProvider> = emptyList(),
)

@Serializable
data class MihomoProxy(val name: String, val type: String)

/** Raw type strings deliberately preserve unsupported/future types for diagnostics. */
@Serializable
data class MihomoProxyProvider(val name: String, val type: String)

@Serializable
data class MihomoGroup(
    val name: String,
    val type: String,
    val proxies: List<String> = emptyList(),
    val use: List<String> = emptyList(),
    val includeAll: Boolean = false,
    val includeAllProxies: Boolean = false,
    val includeAllProviders: Boolean = false,
    /** Preserve Mihomo regexp2 syntax; do not evaluate with Kotlin Regex. */
    val filter: String? = null,
    val excludeFilter: String? = null,
    val excludeType: String? = null,
    val emptyFallback: String? = null,
    /** Presentation metadata is native configuration data, not permission to fetch an icon URL. */
    val hidden: Boolean = false,
    val icon: String? = null,
) {
    val includesAllProviders: Boolean get() = includeAll || includeAllProviders
    val includesAllProxies: Boolean get() = includeAll || includeAllProxies
}
