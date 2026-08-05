package com.danila.nimbo.network

import com.danila.nimbo.model.NetworkPreset
import com.danila.nimbo.utils.NetworkPresetType

object NetworkPresetMatcher {
    data class Match(val preset: NetworkPreset, val score: Int, val reasons: List<String>)

    fun bestMatch(
        presets: List<NetworkPreset>,
        snapshot: NetworkContextSnapshot
    ): Match? = presets.mapNotNull { match(it, snapshot) }
        .maxWithOrNull(compareBy<Match> { it.score }.thenBy { it.preset.updatedAtMs })

    fun match(preset: NetworkPreset, snapshot: NetworkContextSnapshot): Match? {
        var score = preset.priority.coerceIn(-100, 100) * 1_000
        val reasons = mutableListOf<String>()
        var hasExplicitRule = false

        fun requireRule(condition: Boolean, points: Int, reason: String): Boolean {
            hasExplicitRule = true
            if (!condition) return false
            score += points
            reasons += reason
            return true
        }

        preset.matchSsid?.trim()?.takeIf { it.isNotEmpty() }?.let { expected ->
            if (!requireRule(snapshot.ssid.equals(expected, ignoreCase = true), 220, "ssid")) return null
        }
        preset.matchCarrierName?.trim()?.takeIf { it.isNotEmpty() }?.let { expected ->
            if (!requireRule(snapshot.carrierName.equals(expected, ignoreCase = true), 180, "carrier")) return null
        }
        NetworkTransport.fromStored(preset.matchTransport)?.let { expected ->
            if (!requireRule(snapshot.transport == expected, 100, "transport")) return null
        }
        preset.matchCaptivePortal?.let { expected ->
            if (!requireRule(snapshot.captivePortal == expected, 90, "captivePortal")) return null
        }
        preset.matchMetered?.let { expected ->
            if (!requireRule(snapshot.metered == expected, 50, "metered")) return null
        }
        preset.matchRoaming?.let { expected ->
            if (!requireRule(snapshot.roaming == expected, 50, "roaming")) return null
        }
        preset.matchCharging?.let { expected ->
            if (!requireRule(snapshot.charging == expected, 20, "charging")) return null
        }
        preset.minimumBatteryPercent?.coerceIn(0, 100)?.let { minimum ->
            val actual = snapshot.batteryPercent ?: return null
            if (!requireRule(actual >= minimum, 10, "battery")) return null
        }

        if (!hasExplicitRule) {
            val fallbackMatches = when (preset.type) {
                NetworkPresetType.HOME -> snapshot.transport == NetworkTransport.WIFI &&
                    !snapshot.metered && !snapshot.captivePortal
                NetworkPresetType.PUBLIC_WIFI -> snapshot.transport == NetworkTransport.WIFI &&
                    (snapshot.metered || snapshot.captivePortal)
                NetworkPresetType.ROAMING -> snapshot.transport == NetworkTransport.CELLULAR && snapshot.roaming
                NetworkPresetType.OTHER -> true
            }
            if (!fallbackMatches) return null
            score += if (preset.type == NetworkPresetType.OTHER) 1 else 5
            reasons += "legacy:${preset.type.name.lowercase()}"
        }

        return Match(preset, score, reasons)
    }
}
