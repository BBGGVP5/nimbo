package com.danila.nimbo.network

import kotlin.math.roundToInt

/** Presentation only. Measurements and cache retain full GET milliseconds. */
internal fun displayPingMs(raw: Int?, protocol: Int): Int? = when {
    raw == null || raw < 0 -> raw
    protocol == PingProtocol.NIMBO.id -> (raw / 3.3).roundToInt()
    else -> raw
}

internal fun displayPingLabel(raw: Int?, protocol: Int): String =
    if (raw == null || raw < 0) "—" else
        (if (protocol == PingProtocol.NIMBO.id) "≈" else "") + displayPingMs(raw, protocol)

/** Keep the old dot preference (2); append both (3). Keys match the common UI contract. */
internal enum class PingDisplay(val id: Int, val key: String) {
    NUMERIC(0, "numeric"), BARS(1, "bars"), BOTH(3, "both"), DOTS(2, "dots");

    companion object {
        fun fromId(id: Int): PingDisplay = entries.firstOrNull { it.id == id } ?: NUMERIC
        fun fromKey(key: String): PingDisplay = entries.firstOrNull { it.key == key } ?: NUMERIC
    }
}

internal fun pingBars(latency: Int?, inProgress: Boolean = false): Int = when {
    inProgress || latency == null || latency < 0 -> 0
    latency < 100 -> 4
    latency < 200 -> 3
    latency < 400 -> 2
    else -> 1
}

internal data class PingMeasurementSettings(
    val protocol: Int, val url: String, val timeoutSeconds: Int, val throughProxy: Boolean
)
