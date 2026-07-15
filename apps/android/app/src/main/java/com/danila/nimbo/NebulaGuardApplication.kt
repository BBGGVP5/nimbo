package com.danila.nimbo

import android.app.Application
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.danila.nimbo.network.UpdateWorker
import com.danila.nimbo.utils.NotificationManager
import com.danila.nimbo.utils.Logger
import java.util.concurrent.TimeUnit

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
        preferencesManager = com.danila.nimbo.utils.PreferencesManager(this)
        Logger.init(this)
        ensureXrayCoreLoaded()
        NotificationManager.createNotificationChannels(this)
        scheduleUpdateCheck()
    }

    private fun scheduleUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateWorkRequest = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "update_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateWorkRequest
        )
        Log.d(TAG, "Scheduled periodic update check every 1 hour")
    }
}
