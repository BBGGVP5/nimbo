package com.danila.nimbo.model

import com.danila.nimbo.utils.NetworkPresetType
import com.google.gson.annotations.SerializedName

data class NetworkPreset(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: NetworkPresetType,
    @SerializedName("iconGlyph") val iconGlyph: String? = null,
    @SerializedName("serverHost") val serverHost: String? = null,
    @SerializedName("serverPort") val serverPort: Int? = null,
    @SerializedName("serverUuid") val serverUuid: String? = null,
    @SerializedName("serverProfileUrl") val serverProfileUrl: String? = null,
    @SerializedName("selectedServerKeys") val selectedServerKeys: List<String> = emptyList(),
    @SerializedName("matchSsid") val matchSsid: String? = null,
    @SerializedName("matchCarrierName") val matchCarrierName: String? = null,
    @SerializedName("matchTransport") val matchTransport: String? = null,
    @SerializedName("matchMetered") val matchMetered: Boolean? = null,
    @SerializedName("matchRoaming") val matchRoaming: Boolean? = null,
    @SerializedName("matchCaptivePortal") val matchCaptivePortal: Boolean? = null,
    @SerializedName("matchCharging") val matchCharging: Boolean? = null,
    @SerializedName("minimumBatteryPercent") val minimumBatteryPercent: Int? = null,
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("smartGroupId") val smartGroupId: String? = null,
    @SerializedName("killSwitch") val killSwitch: Boolean = false,
    @SerializedName("autoBypassByNetwork") val autoBypassByNetwork: Boolean = true,
    @SerializedName("pingThroughProxy") val pingThroughProxy: Boolean = false,
    @SerializedName("pingOnStartup") val pingOnStartup: Boolean = true,
    @SerializedName("pingOnUpdate") val pingOnUpdate: Boolean = true,
    @SerializedName("updateSubOnStartup") val updateSubOnStartup: Boolean = true,
    @SerializedName("subscriptionAutoUpdate") val subscriptionAutoUpdate: Boolean = true,
    @SerializedName("createdAtMs") val createdAtMs: Long = System.currentTimeMillis(),
    @SerializedName("updatedAtMs") val updatedAtMs: Long = System.currentTimeMillis()
)

