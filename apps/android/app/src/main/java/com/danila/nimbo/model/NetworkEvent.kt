package com.danila.nimbo.model

import com.google.gson.annotations.SerializedName

enum class NetworkEventType {
    NETWORK_CHANGED,
    CAPTIVE_PORTAL,
    PORTAL_AUTHORIZED,
    VPN_CONNECTING,
    VPN_CONNECTED,
    VPN_DISCONNECTED,
    HEALTH_CHECK_FAILED,
    RECOVERY_SCHEDULED,
    RECOVERY_SUCCEEDED,
    SMART_SERVER_SWITCHED,
    TRAFFIC_LIMIT_REACHED
}

enum class NetworkEventSeverity { INFO, WARNING, ERROR }

data class NetworkEvent(
    @SerializedName("id") val id: String,
    @SerializedName("timestampMs") val timestampMs: Long,
    @SerializedName("type") val type: NetworkEventType,
    @SerializedName("title") val title: String,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("severity") val severity: NetworkEventSeverity = NetworkEventSeverity.INFO,
    @SerializedName("serverName") val serverName: String? = null,
    @SerializedName("transport") val transport: String? = null
)
