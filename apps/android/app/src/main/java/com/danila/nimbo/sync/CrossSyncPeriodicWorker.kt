package com.danila.nimbo.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.PreferencesManager
import java.util.concurrent.TimeUnit

class CrossSyncPeriodicWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = PreferencesManager(applicationContext)
        val devices = preferences.crossSyncPairedDevices
        if (devices.isEmpty()) return Result.success()
        val enabled = devices.filter { it.autoSync }
        if (enabled.isEmpty()) {
            Logger.i(TAG, "Auto-sync disabled for all devices, skipping periodic run")
            return Result.success()
        }
        return try {
            for (device in enabled) {
                runCatching {
                    val result = PairedSyncEngine.syncOnce(preferences, device) { url, name ->
                        addSubscriptionSkeleton(preferences, url, name)
                    }
                    if (result.unpaired) {
                        Logger.i(TAG, "Desktop no longer paired, clearing local record")
                        preferences.crossSyncPairedDevices =
                            preferences.crossSyncPairedDevices.filterNot { it.deviceId == device.deviceId }
                    }
                }.onFailure { cause ->
                    // ПК может быть выключен или вне сети: не накапливаем ретраи,
                    // следующий периодический запуск попробует снова.
                    Logger.w(TAG, "Periodic sync failed for ${device.name}: ${cause.message}")
                }
            }
            Result.success()
        } catch (cause: Throwable) {
            Logger.w(TAG, "Periodic sync failed: ${cause.message}")
            Result.success()
        }
    }

    private fun addSubscriptionSkeleton(
        preferences: PreferencesManager,
        url: String,
        name: String?
    ) {
        runCatching {
            val profiles = preferences.loadProfiles()
            val exists = profiles.any {
                CrossSyncProtocol.canonicalSubscriptionUrl(it.url) ==
                    CrossSyncProtocol.canonicalSubscriptionUrl(url)
            }
            if (exists || url.isBlank()) return
            val trimmedName = name?.trim().orEmpty()
            val profile = SubscriptionProfile(
                url = url.trim(),
                name = trimmedName,
                customName = trimmedName.takeIf { it.isNotBlank() }
            )
            preferences.saveProfiles(profiles + profile)
            Logger.i(TAG, "Added subscription from sync: ${url.take(80)}")
        }
    }

    private companion object {
        const val TAG = "CrossSyncWorker"
    }
}

object CrossSyncScheduler {
    private const val WORK_NAME = "nimbo_cross_sync_periodic"
    private const val PERIOD_HOURS = 3L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CrossSyncPeriodicWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Logger.i("CrossSync", "Periodic cross-sync scheduled every ${PERIOD_HOURS}h")
    }
}