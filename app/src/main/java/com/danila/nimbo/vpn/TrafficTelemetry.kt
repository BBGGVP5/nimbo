package com.danila.nimbo.vpn

import com.danila.nimbo.mihomo.MihomoReply
import com.danila.nimbo.mihomo.MihomoException
import com.google.gson.JsonObject

enum class TrafficMeasurementScope { UNAVAILABLE, APP_UID, DEVICE, MIHOMO }

data class RouteTraffic(
    val proxyUpload: Long, val proxyDownload: Long,
    val directUpload: Long, val directDownload: Long
) {
    val proxyBytes: Double get() = proxyUpload.toDouble() + proxyDownload.toDouble()
    val directBytes: Double get() = directUpload.toDouble() + directDownload.toDouble()
    /** Zero measured traffic is an empty ring, never a claimed 100% proxy route. */
    val proxyShare: Double? get() = (proxyBytes + directBytes).takeIf { it > 0 }?.let { proxyBytes / it }
}

data class NativeTrafficTelemetry(
    val generation: Long, val upload: Long, val download: Long,
    val route: RouteTraffic?, val tcpConnections: Long?, val udpConnections: Long?
)

internal object TrafficTelemetry {
    private val counterPattern = Regex("0|[1-9][0-9]{0,18}")
    private fun counter(data: JsonObject, key: String): Long? = data[key]?.let {
        if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber) return@let null
        it.toString().takeIf(counterPattern::matches)?.toLongOrNull()
    }

    fun decode(reply: MihomoReply, expectedGeneration: Long): NativeTrafficTelemetry? {
        if (reply.generation != expectedGeneration) return null
        val data = reply.data
        val upload = counter(data, "upload") ?: return null
        val download = counter(data, "download") ?: return null
        val route = if (data["routeAvailable"]?.toString() == "true") {
            val values = listOf("proxyUpload", "proxyDownload", "directUpload", "directDownload")
                .map { counter(data, it) }
            if (values.all { it != null }) RouteTraffic(values[0]!!, values[1]!!, values[2]!!, values[3]!!) else null
        } else null
        return NativeTrafficTelemetry(reply.generation, upload, download, route,
            counter(data, "tcpConnections"), counter(data, "udpConnections"))
    }
}

internal data class TrafficDelta(val upload: Long, val download: Long)

/** Native telemetry is optional. Failures must not escape into the connection lifecycle. */
internal class MihomoTelemetryPoller {
    private var unavailableGeneration: Long? = null
    fun reset() { unavailableGeneration = null }
    fun read(generation: Long, fetch: (Long) -> MihomoReply): NativeTrafficTelemetry? {
        if (unavailableGeneration == generation) return null
        return try {
            TrafficTelemetry.decode(fetch(generation), generation)
        } catch (error: Exception) {
            if (error is MihomoException && error.code in setOf(
                    "UNKNOWN_OPERATION", "UNSUPPORTED_OPERATION", "NATIVE_UNAVAILABLE", "INVALID_OPERATION", "INVALID_REQUEST"
                )) unavailableGeneration = generation
            null
        }
    }
}

/** One core generation per window; stale/decreasing counters must never add another session's bytes. */
internal class TrafficTelemetryWindow(private val generation: Long) {
    private var upload = 0L
    private var download = 0L
    fun sample(value: NativeTrafficTelemetry): TrafficDelta? {
        if (value.generation != generation || value.upload < upload || value.download < download) return null
        return TrafficDelta(value.upload - upload, value.download - download).also {
            upload = value.upload
            download = value.download
        }
    }
}
