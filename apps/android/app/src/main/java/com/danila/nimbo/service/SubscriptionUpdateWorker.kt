package com.danila.nimbo.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.SubscriptionLogoCache

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

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting subscription auto-update")
        Logger.d(TAG, "Starting subscription auto-update")

        // Инициализируем SubscriptionManager и RemnawaveApiClient в контексте воркера
        SubscriptionManager.init(applicationContext)
        com.danila.nimbo.network.RemnawaveApiClient.init(applicationContext)

        try {
            val profiles = preferencesManager.loadProfiles()

            if (profiles.isEmpty()) {
                Log.d(TAG, "No profiles to update")
                return Result.success()
            }

            var updatedCount = 0
            val updatedProfiles = mutableListOf<com.danila.nimbo.ui.screens.SubscriptionProfile>()

            for (profile in profiles) {
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
                    updatedCount++
                    Log.d(TAG, "Updated profile: ${profile.name}")

                } catch (e: Exception) {
                    Log.e(TAG, "Error updating profile ${profile.name}", e)
                    Logger.e(TAG, "Error updating profile ${profile.name}: ${e.message}")
                    // При ошибке сохраняем старый профиль без изменений
                    updatedProfiles.add(profile)
                }
            }

            // Сохраняем обновлённые профили обратно в SharedPreferences
            if (updatedCount > 0) {
                preferencesManager.saveProfiles(updatedProfiles)
                Log.d(TAG, "Saved $updatedCount updated profiles to SharedPreferences")
            }

            Log.d(TAG, "Auto-update completed. Updated $updatedCount profiles")
            Logger.d(TAG, "Auto-update completed. Updated $updatedCount profiles")

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Auto-update failed", e)
            Logger.e(TAG, "Auto-update failed: ${e.message}")
            return Result.retry()
        }
    }
}
