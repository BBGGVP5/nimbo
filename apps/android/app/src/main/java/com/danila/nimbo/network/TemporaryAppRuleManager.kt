package com.danila.nimbo.network

import android.content.Context
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.RoutingConfigurationApplier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class TemporaryAppRoute { VPN, DIRECT }

data class TemporaryAppRule(
    val packageName: String,
    val route: TemporaryAppRoute,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val previousMode: Int,
    val wasInBypassList: Boolean,
    val wasInVpnOnlyList: Boolean
)

object TemporaryAppRuleManager {
    private const val KEY_RULES = "temporary_app_rules_v1"
    private val gson = Gson()

    fun activeRules(context: Context, nowMs: Long = System.currentTimeMillis()): List<TemporaryAppRule> {
        expire(context, nowMs)
        return read(context).filter { it.expiresAtMs > nowMs }
    }

    fun apply(
        context: Context,
        packageName: String,
        route: TemporaryAppRoute,
        durationMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): TemporaryAppRule {
        require(packageName.isNotBlank())
        val preferences = PreferencesManager(context)
        expire(context, nowMs)
        remove(context, packageName, restore = true)

        val bypass = preferences.getAppBypassList().toMutableSet()
        val vpnOnly = preferences.getAppVpnOnlyList().toMutableSet()
        val rule = TemporaryAppRule(
            packageName = packageName,
            route = route,
            createdAtMs = nowMs,
            expiresAtMs = nowMs + durationMs.coerceAtLeast(60_000L),
            previousMode = preferences.proxyByApp,
            wasInBypassList = packageName in bypass,
            wasInVpnOnlyList = packageName in vpnOnly
        )

        when (route) {
            TemporaryAppRoute.DIRECT -> {
                if (preferences.proxyByApp == 0) preferences.proxyByApp = 1
                bypass += packageName
                vpnOnly -= packageName
            }
            TemporaryAppRoute.VPN -> {
                if (preferences.proxyByApp == 2) vpnOnly += packageName else bypass -= packageName
                if (preferences.proxyByApp == 0) {
                    preferences.proxyByApp = 2
                    vpnOnly += packageName
                }
            }
        }
        preferences.setAppBypassList(bypass)
        preferences.setAppVpnOnlyList(vpnOnly)
        write(context, read(context).filterNot { it.packageName == packageName } + rule)
        RoutingConfigurationApplier.applyToActiveTunnel(context)
        return rule
    }

    fun remove(context: Context, packageName: String, restore: Boolean = true) {
        val rules = read(context)
        val rule = rules.lastOrNull { it.packageName == packageName } ?: return
        if (restore) restore(context, rule)
        write(context, rules.filterNot { it.packageName == packageName })
        RoutingConfigurationApplier.applyToActiveTunnel(context)
    }

    fun expire(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val rules = read(context)
        val expired = rules.filter { it.expiresAtMs <= nowMs }
        if (expired.isEmpty()) return
        expired.forEach { restore(context, it) }
        write(context, rules.filter { it.expiresAtMs > nowMs })
        RoutingConfigurationApplier.applyToActiveTunnel(context)
    }

    private fun restore(context: Context, rule: TemporaryAppRule) {
        val preferences = PreferencesManager(context)
        val bypass = preferences.getAppBypassList().toMutableSet()
        val vpnOnly = preferences.getAppVpnOnlyList().toMutableSet()
        if (rule.wasInBypassList) bypass += rule.packageName else bypass -= rule.packageName
        if (rule.wasInVpnOnlyList) vpnOnly += rule.packageName else vpnOnly -= rule.packageName
        preferences.setAppBypassList(bypass)
        preferences.setAppVpnOnlyList(vpnOnly)
    }

    private fun read(context: Context): List<TemporaryAppRule> {
        val raw = PreferencesManager(context).getString(KEY_RULES, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<TemporaryAppRule>>(
                raw,
                object : TypeToken<List<TemporaryAppRule>>() {}.type
            ).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, rules: List<TemporaryAppRule>) {
        PreferencesManager(context).setString(KEY_RULES, gson.toJson(rules))
    }
}
