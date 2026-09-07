package com.danila.nimbo.network

enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    NONE;

    companion object {
        fun fromStored(value: String?): NetworkTransport? = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        }
    }
}

data class NetworkContextSnapshot(
    val transport: NetworkTransport,
    val ssid: String? = null,
    val carrierName: String? = null,
    val metered: Boolean = false,
    val roaming: Boolean = false,
    val captivePortal: Boolean = false,
    val validated: Boolean = false,
    val charging: Boolean = false,
    val batteryPercent: Int? = null
)
