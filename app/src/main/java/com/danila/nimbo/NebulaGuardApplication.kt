package com.danila.nimbo

import android.app.Application
import android.util.Log
import com.danila.nimbo.network.UpdateManager
import com.danila.nimbo.network.UpdateWorkScheduler
import com.danila.nimbo.service.SubscriptionUpdateScheduler
import com.danila.nimbo.utils.NotificationManager
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.utils.AppVisibilityTracker

class NebulaGuardApplication : Application() {

    companion object {
        private const val TAG = "NebulaGuardApp"

        @Volatile
        private var nativeCoreLoaded = false

        lateinit var instance: NebulaGuardApplication
            private set

        fun ensureXrayCoreLoaded() {
            if (nativeCoreLoaded) return

            synchronized(this) {
                if (nativeCoreLoaded) return

                try {
                    System.loadLibrary("gojni")
                    nativeCoreLoaded = true
                    Log.d(TAG, "Loaded native library: gojni")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load native library gojni", e)
                    throw e
                }
            }
        }
    }

    lateinit var preferencesManager: com.danila.nimbo.utils.PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Application.getProcessName() == packageName + com.danila.nimbo.vpn.NimboPingService.PROCESS_SUFFIX) {
            // A probe must not start update/sync schedulers, servers, or VPN recovery.
            ensureXrayCoreLoaded()
            return
        }
        registerActivityLifecycleCallbacks(AppVisibilityTracker)
        preferencesManager = com.danila.nimbo.utils.PreferencesManager(this)
        Logger.init(this)
        // HWID обязан уходить с любым запросом подписки, а не только из UI.
        // Процесс поднимают и VPN-сервис (always-on, плитка, автозапуск), и
        // фоновый воркер; без init заголовок x-hwid пустой, панель отвечает
        // заглушкой-уведомлением, и она ещё и оседает в кэше ответов.
        com.danila.nimbo.network.SubscriptionManager.init(this)
        UpdateManager.confirmPendingInstallation(this)
        ensureXrayCoreLoaded()
        NotificationManager.createNotificationChannels(this)
        UpdateWorkScheduler.schedulePeriodic(this)
        // A periodic WorkManager task is intentionally inexact. Queue one
        // constrained catch-up check at each process start as well, so a device
        // that slept through its flex window still learns about an update as
        // soon as it gets a usable network again.
        UpdateWorkScheduler.enqueueImmediate(this)
        SubscriptionUpdateScheduler.schedule(this)
        com.danila.nimbo.sync.CrossSyncScheduler.schedule(this)
        com.danila.nimbo.sync.CrossSyncDiscoveryEngine.initBackground(this)
        com.danila.nimbo.sync.CrossSyncEmbeddedServer.initBackground(this)
        com.danila.nimbo.utils.CustomAppIconManager.ensureCustomIconFile(this)
        Log.d(TAG, "Ensured periodic background update check")
    }
}
