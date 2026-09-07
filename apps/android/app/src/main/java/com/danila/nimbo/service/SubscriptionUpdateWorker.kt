package com.danila.nimbo.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.network.SubscriptionRefreshPolicy
import com.danila.nimbo.utils.NotificationManager
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.SubscriptionLogoCache
import com.danila.nimbo.utils.AppVisibilityTracker
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState

/**
 * Worker для фонового обновления подписок
 */
class SubscriptionUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SubscriptionUpdateWorker"
    }

    private val preferencesManager = PreferencesManager(applicationContext)

    private fun isVpnActive(): Boolean =
        preferencesManager.vpnConnectionDesired || VpnManager.state.value != VpnState.DISCONNECTED

    override suspend fun doWork(): Result {
        if (!SubscriptionRefreshSchedulePolicy.canRefreshSubscriptions(
                autoUpdateEnabled = preferencesManager.subscriptionAutoUpdate,
                vpnConnectionDesired = preferencesManager.vpnConnectionDesired,
                vpnStateActive = VpnManager.state.value != VpnState.DISCONNECTED
            )
        ) {
            Log.d(TAG, "Auto-update skipped: disabled or VPN is active")
            if (preferencesManager.subscriptionAutoUpdate) {
                SubscriptionUpdateScheduler.scheduleNext(applicationContext)
            }
            return Result.success()
        }
        Log.d(TAG, "Starting subscription auto-update")
        Logger.d(TAG, "Starting subscription auto-update")

        // Инициализируем SubscriptionManager и RemnawaveApiClient в контексте воркера
        SubscriptionManager.init(applicationContext)
        com.danila.nimbo.network.RemnawaveApiClient.init(applicationContext)

        try {
            val profiles = preferencesManager.loadProfiles()

            if (profiles.isEmpty()) {
                Log.d(TAG, "No profiles to update")
                SubscriptionUpdateScheduler.scheduleNext(applicationContext)
                return Result.success()
            }

            val nowMs = System.currentTimeMillis()
            val intervalSeconds = preferencesManager.subscriptionUpdateInterval
            val dueUrls = profiles
                .filter { profile ->
                    SubscriptionRefreshSchedulePolicy.isDue(
                        nowMs = nowMs,
                        lastSuccessMs = preferencesManager.getLastSubscriptionUpdateTime(profile.url),
                        configuredSeconds = intervalSeconds
                    )
                }
                .mapTo(mutableSetOf()) { it.url }
            if (dueUrls.isEmpty()) {
                Log.d(TAG, "No subscriptions are due yet")
                SubscriptionUpdateScheduler.scheduleNext(applicationContext)
                return Result.success()
            }

            var successfulChecks = 0
            var changedCount = 0
            var failedCount = 0
            val successfulServerCounts = mutableListOf<Int>()
            val updatedProfiles = mutableListOf<com.danila.nimbo.ui.screens.SubscriptionProfile>()

            for (profile in profiles) {
                if (profile.url !in dueUrls) {
                    updatedProfiles.add(profile)
                    continue
                }
                try {
                    Log.d(TAG, "Updating profile: ${profile.name}")

                    // Загружаем обновлённую подписку
                    val result = SubscriptionManager.load(profile.url)

                    // Remnawave API уже обновил expireTime, используем его напрямую
                    val adjustedDaysUntilExpiry = if (result.expireTime > 0) {
                        val now = System.currentTimeMillis() / 1000
                        (result.expireTime - now) / (24 * 60 * 60)
                    } else {
                        result.daysUntilExpiry
                    }

                    val parsedServers = result.servers.mapNotNull { line ->
                        try {
                            com.danila.nimbo.network.LinkParser.parse(line).copy(profileUrl = profile.url)
                        } catch (e: Exception) {
                            Log.w(TAG, "Parse error: $line", e)
                            null
                        }
                    }
                    val updatedBrandLogo = result.brandLogo ?: profile.brandLogo
                    val updatedBrandLogoCache = SubscriptionLogoCache.prepareCachedLogo(
                        logo = updatedBrandLogo,
                        previousLogo = profile.brandLogo,
                        previousCache = profile.brandLogoCache
                    )
                    val updatedThemeSpec = result.themeSpec ?: profile.themeSpec

                    // Обновляем профиль
                    val updatedProfile = profile.copy(
                        isLoading = false,
                        error = null,
                        name = result.username ?: profile.name,
                        servers = parsedServers.ifEmpty { profile.servers },
                        uploadTotal = result.uploadTotal,
                        downloadTotal = result.downloadTotal,
                        totalTraffic = result.totalTraffic,
                        expireTime = result.expireTime,
                        deviceCount = result.deviceCount,
                        announce = result.announce,
                        username = result.username,
                        daysUntilExpiry = adjustedDaysUntilExpiry,
                        websiteUrl = result.websiteUrl,
                        supportUrl = result.supportUrl,
                        brandLogo = updatedBrandLogo,
                        brandLogoCache = updatedBrandLogoCache,
                        themeSpec = updatedThemeSpec,
                        autoUpdateInterval = result.autoUpdateInterval ?: profile.autoUpdateInterval
                    )
                    updatedThemeSpec?.takeIf { it.isNotBlank() }?.let {
                        preferencesManager.subscriptionThemeSpec = it
                    }

                    updatedProfiles.add(updatedProfile)
                    preferencesManager.setLastSubscriptionUpdateTime(profile.url, nowMs)
                    preferencesManager.setSubscriptionUpdateInterval(
                        profile.url,
                        result.autoUpdateInterval
                    )
                    successfulChecks++
                    if (updatedProfile != profile) {
                        changedCount++
                        successfulServerCounts += updatedProfile.servers.size
                    }
                    Log.d(TAG, "Updated profile: ${profile.name}")

                } catch (e: Exception) {
                    Log.e(TAG, "Error updating profile ${profile.name}", e)
                    Logger.e(TAG, "Error updating profile ${profile.name}: ${e.message}")
                    // При ошибке сохраняем старый профиль без изменений
                    updatedProfiles.add(profile)
                    failedCount++
                }
            }

            // The tunnel may have connected while a network request was in flight.
            // Do not replace the persisted profile list or show a notification in
            // that case; the next scheduled check will retry after disconnection.
            if (isVpnActive()) {
                Log.d(TAG, "Discarding background subscription results because VPN became active")
                SubscriptionUpdateScheduler.scheduleNext(applicationContext)
                return Result.success()
            }

            // Сохраняем обновлённые профили обратно в SharedPreferences
            if (changedCount > 0) {
                preferencesManager.saveProfiles(updatedProfiles)
                SubscriptionUpdateEvents.notifyProfilesChanged()
                Log.d(TAG, "Saved $changedCount changed profiles to SharedPreferences")
            }

            Log.d(TAG, "Auto-update completed. Checked $successfulChecks, changed $changedCount")
            Logger.d(TAG, "Auto-update completed. Checked $successfulChecks, changed $changedCount")

            if (SubscriptionRefreshSchedulePolicy.shouldShowSystemNotification(
                    notificationsEnabled = preferencesManager.notifyOnSubscriptionUpdate,
                    appInForeground = AppVisibilityTracker.isForeground,
                    changedSubscriptions = changedCount,
                    vpnActive = isVpnActive()
                )
            ) {
                NotificationManager.showSubscriptionUpdateNotification(
                    applicationContext,
                    SubscriptionRefreshPolicy.summarize(successfulServerCounts, failedCount)
                )
            }

            if (failedCount > 0) return Result.retry()
            SubscriptionUpdateScheduler.scheduleNext(applicationContext)
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Auto-update failed", e)
            Logger.e(TAG, "Auto-update failed: ${e.message}")
            return Result.retry()
        }
    }
}
