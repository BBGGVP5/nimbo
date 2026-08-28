package com.danila.nimbo.shared.subscription

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionPayloadFormat {
    @SerialName("plain_links") PLAIN_LINKS,
    @SerialName("base64_links") BASE64_LINKS,
    @SerialName("json_links") JSON_LINKS,
    @SerialName("xray_json") XRAY_JSON,
    @SerialName("unknown") UNKNOWN
}

@Serializable
data class NormalizedSubscriptionServer(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String = "",
    val port: Int = 0,
    val transport: String = "",
    val security: String = "",
    val rawConfiguration: String,
    val isNativeXrayJson: Boolean = false
)

@Serializable
data class NormalizedSubscription(
    val parserRevision: Int = SubscriptionParserMigration.currentRevision,
    val title: String = "Подписка",
    val source: String? = null,
    val format: SubscriptionPayloadFormat = SubscriptionPayloadFormat.UNKNOWN,
    val servers: List<NormalizedSubscriptionServer> = emptyList(),
    val diagnosticCode: String? = null
)
