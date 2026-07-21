package com.danila.nimbo.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

enum class UpdateChannel : Serializable {
    STABLE,
    BETA;

    val preferenceValue: String
        get() = name.lowercase()

    companion object {
        fun fromPreference(value: String?): UpdateChannel =
            entries.firstOrNull { it.preferenceValue == value?.lowercase() } ?: STABLE
    }
}

enum class UpdateKind : Serializable {
    VERSION,
    REPAIR
}

data class UpdateInfo(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("changelog") val changelog: String? = null,
    @SerializedName("changelogUrl") val changelogUrl: String? = null,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("forceUpdate") val forceUpdate: Boolean = false,
    @SerializedName("publishDate") val publishDate: String? = null,
    @SerializedName("fileSize") val fileSize: Long = 0L,
    @SerializedName("channel") val channel: UpdateChannel = UpdateChannel.STABLE,
    @SerializedName("kind") val kind: UpdateKind = UpdateKind.VERSION,
    @SerializedName("artifactId") val artifactId: String = "",
    @SerializedName("assetId") val assetId: Long = 0L,
    @SerializedName("assetName") val assetName: String = "",
    @SerializedName("assetUpdatedAt") val assetUpdatedAt: String? = null,
    @SerializedName("sha256") val sha256: String? = null,
    @SerializedName("releaseUrl") val releaseUrl: String? = null
) : Serializable

