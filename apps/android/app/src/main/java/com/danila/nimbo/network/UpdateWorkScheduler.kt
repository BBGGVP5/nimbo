package com.danila.nimbo.network

import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object UpdateSchedulePolicy {
    const val PERIODIC_INTERVAL_MINUTES = 15L
    const val PERIODIC_FLEX_MINUTES = 5L
    const val PERIODIC_WORK_NAME = "update_check_v2_15m"
    const val LEGACY_PERIODIC_WORK_NAME = "update_check"
    const val IMMEDIATE_WORK_NAME = "update_check_immediate"
    const val BACKGROUND_CATCH_UP_WORK_NAME = "update_check_background_catch_up"
}

/** Registers update checks independently from the lifetime of the UI process. */
internal object UpdateWorkScheduler {
    private val connectedNetworkConstraints: Constraints
        get() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(
            UpdateSchedulePolicy.PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            UpdateSchedulePolicy.PERIODIC_FLEX_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(connectedNetworkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueueUniquePeriodicWork(
            UpdateSchedulePolicy.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        // The old hourly request used UPDATE on every process start, so its next
        // execution could be pushed back repeatedly. The versioned 15-minute job
        // is persistent and the obsolete schedule must not keep running beside it.
        workManager.cancelUniqueWork(UpdateSchedulePolicy.LEGACY_PERIODIC_WORK_NAME)
    }

    fun enqueueImmediate(context: Context) {
        enqueueOneTime(
            context = context,
            uniqueName = UpdateSchedulePolicy.IMMEDIATE_WORK_NAME
        )
    }

    /** Runs as the activity leaves the foreground and survives process removal. */
    fun enqueueBackgroundCatchUp(context: Context) {
        enqueueOneTime(
            context = context,
            uniqueName = UpdateSchedulePolicy.BACKGROUND_CATCH_UP_WORK_NAME
        )
    }

    private fun enqueueOneTime(context: Context, uniqueName: String) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(connectedNetworkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueName,
            // Replacing a currently-running worker could cancel the network
            // request exactly while the app was going to background. Keeping
            // the first request preserves its retry/backoff state; the periodic
            // worker remains the durable fallback if Android delays it.
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    internal fun shouldEnqueueImmediate(action: String): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
}
