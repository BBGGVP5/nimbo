package com.danila.nimbo.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class UpdateInfo(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("changelog") val changelog: String? = null,
    @SerializedName("changelogUrl") val changelogUrl: String? = null,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("forceUpdate") val forceUpdate: Boolean = false,
    @SerializedName("publishDate") val publishDate: String? = null,
    @SerializedName("fileSize") val fileSize: Long = 0L
) : Serializable
