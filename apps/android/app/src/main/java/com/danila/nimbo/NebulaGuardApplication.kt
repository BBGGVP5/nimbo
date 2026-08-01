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
        registerActivityLifecycleCallbacks(AppVisibilityTracker)
        preferencesManager = com.danila.nimbo.utils.PreferencesManager(this)
        Logger.init(this)
        UpdateManager.confirmPendingInstallation(this)
        ensureXrayCoreLoaded()
        NotificationManager.createNotificationChannels(this)
        UpdateWorkScheduler.schedulePeriodic(this)
        SubscriptionUpdateScheduler.schedule(this)
        Log.d(TAG, "Ensured periodic background update check")
    }
}
