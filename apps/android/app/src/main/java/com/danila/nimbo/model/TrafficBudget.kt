package com.danila.nimbo.model

import com.google.gson.annotations.SerializedName

enum class TrafficBudgetPeriod { DAY, WEEK, MONTH }
enum class TrafficBudgetAction { NOTIFY, DISCONNECT, BLOCK_UNTIL_RESET }

data class TrafficBudget(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("limitBytes") val limitBytes: Long,
    @SerializedName("period") val period: TrafficBudgetPeriod = TrafficBudgetPeriod.MONTH,
    @SerializedName("action") val action: TrafficBudgetAction = TrafficBudgetAction.NOTIFY,
    @SerializedName("warningPercent") val warningPercent: Int = 80,
    @SerializedName("packageName") val packageName: String? = null,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("cycleAnchorMs") val cycleAnchorMs: Long = System.currentTimeMillis()
)

data class TrafficBudgetUsage(
    @SerializedName("budgetId") val budgetId: String,
    @SerializedName("usedBytes") val usedBytes: Long,
    @SerializedName("cycleStartedAtMs") val cycleStartedAtMs: Long,
    @SerializedName("warningSent") val warningSent: Boolean = false,
    @SerializedName("limitHandled") val limitHandled: Boolean = false
)
