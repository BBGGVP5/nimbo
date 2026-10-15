package com.danila.nimbo.shared.routing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Small offline suffix overlay. Provider rules and stored profiles stay intact. */
object NimboAdBlocking {
    val suffixDomains: List<String> = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googleads.g.doubleclick.net",
        "adservice.google.com", "ads.yahoo.com", "advertising.com", "adsrvr.org", "adnxs.com", "adform.net",
        "adroll.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "scorecardresearch.com",
        "quantserve.com", "ads.facebook.com", "app-measurement.com", "amazon-adsystem.com"
    )

    fun xrayRule(enabled: Boolean, blockTag: String = "block"): JsonObject? = if (!enabled) null else buildJsonObject {
        put("type", "field")
        put("domain", JsonArray(suffixDomains.map { JsonPrimitive("domain:$it") }))
        put("outboundTag", blockTag)
    }

    fun prependXrayRules(rules: JsonArray, enabled: Boolean, blockTag: String = "block"): JsonArray =
        xrayRule(enabled, blockTag)?.let { JsonArray(listOf(it) + rules) } ?: rules
}
