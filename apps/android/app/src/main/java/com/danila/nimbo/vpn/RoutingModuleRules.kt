package com.danila.nimbo.vpn

import com.danila.nimbo.shared.routing.NimboModuleParser
import com.danila.nimbo.shared.routing.NimboModuleRule
import com.danila.nimbo.utils.PreferencesManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Правила пользовательских модулей в виде, понятном Xray.
 *
 * Разбор текста живёт в общем модуле — один и тот же набор обязан вести себя
 * одинаково на Android и iOS. Здесь только перевод разобранных правил в JSON.
 */
object RoutingModuleRules {

    /**
     * Правила включённых модулей.
     *
     * Идут перед правилами профиля: модуль пишет человек под свою задачу, и
     * если он сказал «этот домен напрямую», профиль не должен его перебивать.
     */
    fun build(preferences: PreferencesManager): JSONArray = JSONArray().apply {
        NimboModuleParser.rulesOf(preferences.routingModules()).forEach { rule ->
            toJson(rule)?.let { put(it) }
        }
    }

    private fun toJson(rule: NimboModuleRule): JSONObject? {
        if (rule.domains.isEmpty() && rule.ips.isEmpty()) return null
        return JSONObject().apply {
            put("type", "field")
            put("inboundTag", JSONArray().put("tun-in"))
            if (rule.domains.isNotEmpty()) {
                put("domain", JSONArray().apply { rule.domains.forEach { put(it) } })
            }
            if (rule.ips.isNotEmpty()) {
                put("ip", JSONArray().apply { rule.ips.forEach { put(it) } })
            }
            put("outboundTag", rule.policy.outboundTag)
        }
    }
}
