package com.danila.nimbo.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.danila.nimbo.utils.PreferencesManager
import java.util.concurrent.TimeUnit

/**
 * Менеджер для управления автообновлением подписок
 */
object SubscriptionUpdateScheduler {

    private const val TAG = "SubscriptionUpdate"
    private const val WORK_TAG = "subscription_auto_update"

    /**
     * Планирование автообновления подписок
     * Использует интервал из Remnawave (если доступен) или глобальную настройку
     */
    fun schedule(context: Context) {
        val preferencesManager = PreferencesManager(context)

        if (!preferencesManager.subscriptionAutoUpdate) {
            cancel(context)
            return
        }

        // Получаем интервал авто-обновления из подписки (Remnawave)
        // Если есть несколько подписок, используем минимальный интервал
        val profiles = preferencesManager.loadProfiles()
        val intervalFromSubscription = profiles
            .mapNotNull { preferencesManager.getSubscriptionUpdateInterval(it.url) }
            .minOrNull()

        // Если есть интервал из подписки, используем его, иначе - глобальную настройку
        val intervalSeconds = intervalFromSubscription?.toLong()
            ?: preferencesManager.subscriptionUpdateInterval.toLong()

        // Конвертируем часы в минуты (Remnawave передаёт интервал в часах)
        val intervalMinutes = if (intervalFromSubscription != null) {
            (intervalSeconds * 60).coerceAtLeast(15) // Минимум 15 минут
        } else {
            (intervalSeconds / 60).coerceAtLeast(15) // Минимум 15 минут
        }

        Log.d(TAG, "Scheduling auto-update every $intervalMinutes minutes (from ${if (intervalFromSubscription != null) "subscription" else "settings"})")
        if (intervalFromSubscription != null) {
            Log.d(TAG, "Interval from Remnawave subscription: $intervalFromSubscription hours")
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            updateRequest
        )
    }

    /**
     * Отмена автообновления
     */
    fun cancel(context: Context) {
        Log.d(TAG, "Cancelling auto-update")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
    }

    /**
     * Перепланирование при изменении настроек
     */
    fun reschedule(context: Context) {
        cancel(context)
        schedule(context)
    }
}
