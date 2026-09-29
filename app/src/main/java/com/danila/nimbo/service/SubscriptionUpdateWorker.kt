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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.danila.nimbo.utils.isNoticePlaceholderServer

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

    override suspend fun doWork(): Result =
        com.danila.nimbo.utils.BackgroundWorkHistory.track(applicationContext, "subscriptions") { runWork() }

    private suspend fun refreshMihomoSource(
        originalProfiles: List<com.danila.nimbo.ui.screens.SubscriptionProfile>,
        updatedProfiles: MutableList<com.danila.nimbo.ui.screens.SubscriptionProfile>,
        pendingIntervals: MutableList<Pair<String, Int?>>
    ) {
        val original = originalProfiles.firstOrNull()
            ?: throw com.danila.nimbo.mihomo.MihomoException("PROFILE_NOT_FOUND")
        val sourceUrl = original.mihomoSourceUrl?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw com.danila.nimbo.mihomo.MihomoException("INVALID_SUBSCRIPTION_URL")
        val expectedHashes = originalProfiles.associate {
            (it.mihomoDocumentId ?: "default") to com.danila.nimbo.mihomo.MihomoProtocol.sourceHash(
                it.rawConfig ?: throw com.danila.nimbo.mihomo.MihomoException("PROFILE_SOURCE_MISSING")
            )
        }
        val candidate = com.danila.nimbo.mihomo.MihomoSubscriptionFetcher.fetch(sourceUrl)
        currentCoroutineContext().ensureActive()
        val documents = SubscriptionManager.detectNativeMihomoDocuments(candidate)
            .ifEmpty { throw com.danila.nimbo.mihomo.MihomoException("INVALID_YAML") }
        // Inspect and preflight every member before atomically replacing the source set.
        documents.forEach { document ->
            com.danila.nimbo.mihomo.MihomoBridge.inspect(document.source)
            com.danila.nimbo.mihomo.MihomoBridge.preflight(document.source)
        }
        if (isVpnActive()) throw com.danila.nimbo.mihomo.MihomoException("PROFILE_ACTIVE")
        currentCoroutineContext().ensureActive()
        val latest = preferencesManager.loadProfiles().filter {
            com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(it) && it.mihomoSourceUrl?.trim() == sourceUrl
        }
        val latestHashes = latest.associate {
            (it.mihomoDocumentId ?: "default") to com.danila.nimbo.mihomo.MihomoProtocol.sourceHash(it.rawConfig.orEmpty())
        }
        if (latestHashes != expectedHashes) {
            throw com.danila.nimbo.mihomo.MihomoException("STALE_PROFILE")
        }
        val committed = com.danila.nimbo.mihomo.MihomoProfiles.importDocuments(
            applicationContext, sourceUrl, documents, expectedSourceHashes = expectedHashes
        ) { base, previous ->
            base.copy(
                uploadTotal = previous?.uploadTotal ?: original.uploadTotal,
                downloadTotal = previous?.downloadTotal ?: original.downloadTotal,
                totalTraffic = previous?.totalTraffic ?: original.totalTraffic,
                expireTime = previous?.expireTime ?: original.expireTime,
                deviceCount = previous?.deviceCount ?: original.deviceCount,
                deviceLimit = previous?.deviceLimit ?: original.deviceLimit,
                onlineDevices = previous?.onlineDevices ?: original.onlineDevices,
                announce = previous?.announce ?: original.announce,
                username = previous?.username ?: original.username,
                daysUntilExpiry = previous?.daysUntilExpiry ?: original.daysUntilExpiry,
                websiteUrl = previous?.websiteUrl ?: original.websiteUrl,
                supportUrl = previous?.supportUrl ?: original.supportUrl,
                autoUpdateInterval = previous?.autoUpdateInterval ?: original.autoUpdateInterval,
                brandLogo = previous?.brandLogo ?: original.brandLogo,
                brandLogoCache = previous?.brandLogoCache ?: original.brandLogoCache,
                themeSpec = previous?.themeSpec ?: original.themeSpec,
                error = null,
                isLoading = false
            )
        }
        updatedProfiles += committed
        pendingIntervals += sourceUrl to original.autoUpdateInterval
    }

    private suspend fun runWork(): Result {
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
                    val mihomo = com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(profile)
                    val hasSource = profile.mihomoSourceUrl?.isNotBlank() == true
                    (!mihomo || hasSource) && SubscriptionRefreshSchedulePolicy.isDue(
                        nowMs = nowMs,
                        lastSuccessMs = preferencesManager.getLastSubscriptionUpdateTime(
                            if (mihomo) profile.mihomoSourceUrl!!.trim() else profile.url
                        ),
                        configuredSeconds = intervalSeconds
                    )
                }
                .mapTo(mutableSetOf()) { profile ->
                    if (com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(profile))
                        profile.mihomoSourceUrl!!.trim() else profile.url
                }
            if (dueUrls.isEmpty()) {
                Log.d(TAG, "No subscriptions are due yet")
                SubscriptionUpdateScheduler.scheduleNext(applicationContext)
                return Result.success()
            }

            var successfulChecks = 0
            var changedCount = 0
            var failedCount = 0
            val mihomoFailures = mutableMapOf<String, String>()
            val successfulServerCounts = mutableListOf<Int>()
            val updatedProfiles = mutableListOf<com.danila.nimbo.ui.screens.SubscriptionProfile>()
            val pendingIntervals = mutableListOf<Pair<String, Int?>>()
            var pendingThemeSpec: String? = null
            val refreshedMihomoSources = mutableSetOf<String>()
            val discoveredTemplates = mutableMapOf<String, com.danila.nimbo.mihomo.MihomoSubscriptionTemplates>()

            for (profile in profiles) {
                currentCoroutineContext().ensureActive()
                val mihomo = com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(profile)
                val updateKey = if (mihomo) profile.mihomoSourceUrl?.trim().orEmpty() else profile.url
                if (updateKey !in dueUrls) {
                    updatedProfiles.add(profile)
                    continue
                }
                if (mihomo && !refreshedMihomoSources.add(updateKey)) continue
                if (mihomo && discoveredTemplates.any { (parentUrl, templates) ->
                        templates.sourceUrl == updateKey || profile.mihomoParentUrl == parentUrl
                    }) {
                    updatedProfiles.add(profile)
                    continue
                }
                if (isVpnActive()) {
                    SubscriptionUpdateScheduler.scheduleNext(applicationContext)
                    return Result.success()
                }
                try {
                    Log.d(TAG, "Updating profile: ${profile.name}")
                    if (mihomo) {
                        val sourceProfiles = profiles.filter {
                            com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(it) &&
                                it.mihomoSourceUrl?.trim() == updateKey
                        }
                        refreshMihomoSource(sourceProfiles, updatedProfiles, pendingIntervals)
                        successfulChecks++
                        val refreshed = updatedProfiles.filter {
                            com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(it) &&
                                it.mihomoSourceUrl?.trim() == updateKey
                        }
                        val oldSourceProfiles = profiles.filter {
                            com.danila.nimbo.mihomo.MihomoProfiles.isMihomo(it) &&
                                it.mihomoSourceUrl?.trim() == updateKey
                        }
                        if (refreshed != oldSourceProfiles) {
                            changedCount++
                            successfulServerCounts += refreshed.sumOf { it.servers.size }
                        }
                        continue
                    }

                    // Загружаем обновлённую подписку
                    val result = SubscriptionManager.load(profile.url)
                    result.mihomoTemplates?.let { discoveredTemplates[profile.url] = it }
                    currentCoroutineContext().ensureActive()
                    if (isVpnActive()) {
                        SubscriptionUpdateScheduler.scheduleNext(applicationContext)
                        return Result.success()
                    }

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
                    }.let { servers ->
                        // Панель отдаёт уведомление вместо серверов записями на
                        // 127.0.0.1:1 («Включите HWID в настройках»). Список
                        // получается непустым, и проверка ifEmpty ниже его
                        // пропускала — рабочие серверы затирались заглушками в
                        // фоне, молча, а потом «просто не коннектит».
                        val real = servers.filterNot(::isNoticePlaceholderServer)
                        if (real.size != servers.size) {
                            Log.w(
                                TAG,
                                "Subscription ${profile.name} returned ${servers.size - real.size} notice placeholders; they are not servers"
                            )
                        }
                        real
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
                        pendingThemeSpec = it
                    }

                    updatedProfiles.add(updatedProfile)
                    // Commit scheduling metadata only after profiles are saved.
                    // Cancellation must not mark discarded results as up to date.
                    pendingIntervals += profile.url to result.autoUpdateInterval
                    successfulChecks++
                    if (updatedProfile != profile) {
                        changedCount++
                        successfulServerCounts += updatedProfile.servers.size
                    }
                    Log.d(TAG, "Updated profile: ${profile.name}")

                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating profile ${profile.name}", e)
                    Logger.e(TAG, "Error updating profile ${profile.name}: ${e.message}")
                    // При ошибке сохраняем старый профиль без изменений.
                    // Mihomo errors are deliberately reduced to safe codes; the URL/YAML
                    // must never enter logs or persisted diagnostic text.
                    updatedProfiles.add(profile)
                    if (mihomo) {
                        val code = (e as? com.danila.nimbo.mihomo.MihomoException)?.code
                            ?: "MIHOMO_REFRESH_FAILED"
                        mihomoFailures[profile.url] = code
                        // Keep the last valid YAML, but expose a safe failure marker
                        // to the profile UI without persisting the subscription URL.
                        updatedProfiles[updatedProfiles.lastIndex] = profile.copy(error = code)
                    }
                    failedCount++
                }
            }

            currentCoroutineContext().ensureActive()
            // The tunnel may have connected while a network request was in flight.
            // Do not replace the persisted profile list or show a notification in
            // that case; the next scheduled check will retry after disconnection.
            if (isVpnActive()) {
                Log.d(TAG, "Discarding background subscription results because VPN became active")
                SubscriptionUpdateScheduler.scheduleNext(applicationContext)
                return Result.success()
            }

            // Сохраняем обновлённые профили обратно в SharedPreferences
            if (changedCount > 0 || mihomoFailures.isNotEmpty() || discoveredTemplates.isNotEmpty()) {
                // Preserve imports/deletes/edits made while a network refresh was running.
                val originalByUrl = profiles.associateBy { it.url }
                val updatedByUrl = updatedProfiles.associateBy { it.url }
                preferencesManager.updateProfiles { latest ->
                    var next = latest.map { profile ->
                        if (profile == originalByUrl[profile.url]) updatedByUrl[profile.url] ?: profile else profile
                    }
                    discoveredTemplates.forEach { (parentUrl, templates) ->
                        val original = originalByUrl[parentUrl]
                        if (original != null && latest.any { it == original } && !isVpnActive()) {
                            next.firstOrNull { it.url == parentUrl }?.let { parent ->
                                // A simultaneous import/edit wins over the old network snapshot.
                                val originalNative = profiles.filter { templates.belongsTo(it, parent) }
                                val latestNative = latest.filter { templates.belongsTo(it, parent) }
                                if (latestNative == originalNative) {
                                    next = templates.mergeInto(next, parent)
                                    pendingIntervals += templates.sourceUrl to parent.autoUpdateInterval
                                }
                            }
                        }
                    }
                    next
                }
                SubscriptionUpdateEvents.notifyProfilesChanged()
                Log.d(TAG, "Saved $changedCount changed profiles to SharedPreferences")
            }
            pendingThemeSpec?.let { preferencesManager.subscriptionThemeSpec = it }
            for ((url, interval) in pendingIntervals) {
                preferencesManager.setSubscriptionUpdateInterval(url, interval)
                preferencesManager.setLastSubscriptionUpdateTime(url, nowMs)
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

        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Auto-update failed", e)
            Logger.e(TAG, "Auto-update failed: ${e.message}")
            return Result.retry()
        }
    }
}
