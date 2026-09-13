package com.danila.nimbo.network

internal object ActiveRoutePingPolicy {
    fun resultKey(ownerKey: String?, requestedKeys: List<String>?): String? = ownerKey
        ?.takeIf { it.isNotBlank() && (requestedKeys == null || it in requestedKeys) }
}

/** MainViewModel's commit guard: cancelled runs/settings cannot publish pending samples. */
internal class PingRunGuard {
    @Volatile private var serial = 0L
    @Volatile private var settings: PingMeasurementSettings? = null

    fun begin(settings: PingMeasurementSettings): Long {
        this.settings = settings
        serial += 1
        return serial
    }

    fun cancel() { serial += 1 }

    fun accepts(runId: Long, current: PingMeasurementSettings): Boolean =
        serial == runId && settings == current
}
