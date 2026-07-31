package com.danila.nimbo.service

import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState

internal data class QuickSettingsStats(
    val profileName: String,
    val daysUntilExpiry: Long,
    val usedTraffic: Long,
    val totalTraffic: Long,
    val deviceLimit: Int,
    val connectedDevicesOnThisPhone: Int,
    val isVpnConnected: Boolean
)

internal object QuickSettingsStatsProvider {
    fun load(preferencesManager: PreferencesManager): QuickSettingsStats? {
        val profiles = preferencesManager.loadProfiles()
        if (profiles.isEmpty()) return null

        val selectedProfile = findActiveProfile(preferencesManager, profiles) ?: profiles.first()
        val connected = VpnManager.state.value == VpnState.CONNECTED

        return QuickSettingsStats(
            profileName = selectedProfile.name.ifBlank { selectedProfile.username ?: "Профиль" },
            daysUntilExpiry = selectedProfile.daysUntilExpiry,
            usedTraffic = selectedProfile.usedTraffic,
            totalTraffic = selectedProfile.totalTraffic,
            deviceLimit = selectedProfile.deviceCount,
            connectedDevicesOnThisPhone = if (connected) 1 else 0,
            isVpnConnected = connected
        )
    }

    private fun findActiveProfile(
        preferencesManager: PreferencesManager,
        profiles: List<SubscriptionProfile>
    ): SubscriptionProfile? {
        val connectedProfileUrl = preferencesManager.lastConnectedProfileUrl.takeIf { it.isNotBlank() }
        if (connectedProfileUrl != null) {
            profiles.firstOrNull { it.url == connectedProfileUrl }?.let { return it }
        }

        val selectedProfileUrl = preferencesManager.loadLastSelectedProfileUrl()
        if (!selectedProfileUrl.isNullOrBlank()) {
            profiles.firstOrNull { it.url == selectedProfileUrl }?.let { return it }
        }

        return null
    }

    fun formatDays(days: Long): String {
        return when {
            days < -1L || days > 36500L -> "Бессрочно"
            days == -1L -> "Бессрочно"
            days == 0L -> "Сегодня"
            days == 1L -> "1 день"
            days in 2L..4L -> "$days дня"
            else -> "$days дней"
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
            else -> String.format("%.1f GB", bytes / (1024f * 1024f * 1024f))
        }
    }
}

