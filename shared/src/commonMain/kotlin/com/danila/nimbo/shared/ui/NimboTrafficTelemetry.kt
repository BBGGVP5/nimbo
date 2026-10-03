package com.danila.nimbo.shared.ui

import kotlinx.serialization.json.*

/** Measured cumulative outbound bytes, never inferred from server selection. */
data class NimboRouteTraffic(
    val proxyUpload: Long = 0,
    val proxyDownload: Long = 0,
    val directUpload: Long = 0,
    val directDownload: Long = 0
) {
    val valid: Boolean get() = listOf(proxyUpload, proxyDownload, directUpload, directDownload).all { it >= 0 }
    // Add as doubles to avoid overflowing a signed Long for a long-lived session.
    val proxyBytes: Double get() = proxyUpload.toDouble() + proxyDownload.toDouble()
    val directBytes: Double get() = directUpload.toDouble() + directDownload.toDouble()
    val proxyFraction: Float?
        get() = if (!valid || proxyBytes + directBytes == 0.0) null
        else (proxyBytes / (proxyBytes + directBytes)).toFloat()
}

internal fun measuredRoutes(state: NimboUiState): NimboRouteTraffic? =
    state.routeTraffic?.takeIf { state.vpnState == "connected" && state.sessionAvailable != false && it.valid }

internal fun activeConnectionLabel(state: NimboUiState, count: Int?): String =
    count?.takeIf { state.vpnState == "connected" && state.sessionAvailable != false && it >= 0 }?.toString() ?: "Недоступно"

data class NimboTrafficSnapshot(
    val routeTraffic: NimboRouteTraffic? = null,
    val tcpConnections: Int? = null,
    val udpConnections: Int? = null
)

/** Missing/invalid fields stay nullable; zero is a real supported measurement. */
object NimboTelemetryDecoder {
    fun decode(raw: String?): NimboTrafficSnapshot? = runCatching {
        val data = raw?.let { Json.parseToJsonElement(it) as? JsonObject } ?: return null
        fun number(key: String): Long? = (data[key] as? JsonPrimitive)
            ?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 0 }
        // Reject a malformed or failed bridge response, including legacy empties.
        number("upload") ?: return null
        number("download") ?: return null
        val routeValues = listOf("proxyUpload", "proxyDownload", "directUpload", "directDownload").map { number(it) }
        val routes = if ((data["routeAvailable"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true && routeValues.all { it != null }) {
            NimboRouteTraffic(routeValues[0]!!, routeValues[1]!!, routeValues[2]!!, routeValues[3]!!)
        } else null
        fun count(key: String) = number(key)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        NimboTrafficSnapshot(routes, count("tcpConnections"), count("udpConnections"))
    }.getOrNull()
}
