package com.danila.nimbo.network

import android.content.Context
import com.danila.nimbo.model.TrafficBudget
import com.danila.nimbo.model.TrafficBudgetUsage
import com.danila.nimbo.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TrafficBudgetStore {
    private const val KEY_BUDGETS = "traffic_budgets_v1"
    private const val KEY_USAGE = "traffic_budget_usage_v1"
    private val gson = Gson()

    fun budgets(context: Context): List<TrafficBudget> = decodeList(
        PreferencesManager(context).getString(KEY_BUDGETS, null)
    )

    fun saveBudget(context: Context, budget: TrafficBudget) {
        val list = budgets(context).toMutableList()
        val index = list.indexOfFirst { it.id == budget.id }
        if (index >= 0) list[index] = budget else list += budget
        PreferencesManager(context).setString(KEY_BUDGETS, gson.toJson(list))
    }

    fun deleteBudget(context: Context, budgetId: String) {
        PreferencesManager(context).setString(
            KEY_BUDGETS,
            gson.toJson(budgets(context).filterNot { it.id == budgetId })
        )
        saveUsage(context, usage(context).filterKeys { it != budgetId })
    }

    fun usage(context: Context): Map<String, TrafficBudgetUsage> {
        val list: List<TrafficBudgetUsage> = decodeList(
            PreferencesManager(context).getString(KEY_USAGE, null)
        )
        return list.associateBy { it.budgetId }
    }

    fun saveUsage(context: Context, usage: Map<String, TrafficBudgetUsage>) {
        PreferencesManager(context).setString(KEY_USAGE, gson.toJson(usage.values.toList()))
    }

    private inline fun <reified T> decodeList(raw: String?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<T>>(raw, object : TypeToken<List<T>>() {}.type).orEmpty()
        }.getOrDefault(emptyList())
    }
}
