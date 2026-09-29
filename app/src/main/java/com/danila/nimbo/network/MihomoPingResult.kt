package com.danila.nimbo.network

/** Stable IPC vocabulary only; native errors may contain subscription secrets. */
enum class MihomoPingFailure {
    NO_NETWORK, NO_DNS, HTTP_STATUS, GET_FAILED, UNSUPPORTED_NODE,
    INVALID_CONFIG, INVALID_RESPONSE, UNAVAILABLE, REALITY_AUTH_FAILED,
    DNS_FAILED, PROTECTION_FAILED, DIAL_FAILED, PROBE_TIMEOUT;

    companion object {
        fun fromWire(value: String?): MihomoPingFailure? = entries.firstOrNull { it.name == value }
    }
}

internal data class MihomoPingResult(val delayMs: Int, val reason: MihomoPingFailure? = null)
