package com.danila.nimbo.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.danila.nimbo.utils.PreferencesManager
import java.util.concurrent.TimeUnit

/**
 * Менеджер для управления автообновлением подписок
 */
object SubscriptionUpdateScheduler {

    private const val TAG = "SubscriptionUpdate"
    private const val WORK_NAME = "subscription_auto_update"

    /**
     * Гарантирует наличие одной следующей фоновой проверки, не сбрасывая её
     * при каждом открытии Activity.
     */
    fun schedule(context: Context) {
        val preferencesManager = PreferencesManager(context)

        if (!preferencesManager.subscriptionAutoUpdate) {
            cancel(context)
            return
        }

        enqueue(context, ExistingWorkPolicy.KEEP)
    }

    internal fun scheduleNext(context: Context) {
        val preferencesManager = PreferencesManager(context)
        if (!preferencesManager.subscriptionAutoUpdate) return
        enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        val preferencesManager = PreferencesManager(context)
        val delaySeconds = SubscriptionRefreshSchedulePolicy.delaySeconds(
            preferencesManager.subscriptionUpdateInterval
        )
        Log.d(TAG, "Scheduling subscription refresh in $delaySeconds seconds")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = OneTimeWorkRequestBuilder<SubscriptionUpdateWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            policy,
            updateRequest
        )
    }
    
    /**
     * Отмена автообновления
     */
    fun cancel(context: Context) {
        Log.d(TAG, "Cancelling auto-update")
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
    
    /**
     * Перепланирование при изменении настроек
     */
    fun reschedule(context: Context) {
        val preferencesManager = PreferencesManager(context)
        if (!preferencesManager.subscriptionAutoUpdate) {
            cancel(context)
            return
        }
        enqueue(context, ExistingWorkPolicy.REPLACE)
    }
}
