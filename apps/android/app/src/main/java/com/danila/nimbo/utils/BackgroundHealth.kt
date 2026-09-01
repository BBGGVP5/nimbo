package com.danila.nimbo.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/**
 * Почему фоновая проверка обновлений может не сработать у человека, который
 * давно не открывал приложение.
 *
 * Android постепенно душит неиспользуемые приложения: сначала переводит в
 * «редкие» корзины ожидания (job'ы выполняются раз в несколько часов или раз в
 * сутки), затем — с Android 12 — гибернирует их, отменяя все фоновые задачи и
 * отзывая разрешения. Плюс оболочки вроде MIUI умеют принудительно
 * останавливать приложение, после чего WorkManager не восстановится до
 * перезагрузки или ручного запуска.
 *
 * Средствами приложения это не чинится — можно только увидеть и показать
 * пользователю, что именно мешает.
 */
data class BackgroundHealth(
    /** Приложение НЕ в белом списке оптимизации батареи. */
    val batteryOptimized: Boolean,
    /** Корзина ожидания: ACTIVE / WORKING_SET / FREQUENT / RARE / RESTRICTED. */
    val standbyBucket: String,
    /** Система реально придерживает фоновые задачи. */
    val throttled: Boolean,
    /** Включён авто-отзыв разрешений для неиспользуемых приложений (гибернация). */
    val hibernationEnabled: Boolean,
    /** Уведомления выключены — даже успешная проверка ничего не покажет. */
    val notificationsBlocked: Boolean
) {
    /** Есть ли что показать пользователю. */
    val hasIssues: Boolean
        get() = batteryOptimized || throttled || hibernationEnabled || notificationsBlocked
}

object BackgroundHealthChecker {

    fun inspect(context: Context): BackgroundHealth {
        val appContext = context.applicationContext

        val batteryOptimized = runCatching {
            val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val ignoring = power.isIgnoringBatteryOptimizations(appContext.packageName)
            ignoring.not()
        }.getOrDefault(false)

        val bucket = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@runCatching null
            val usage = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            usage?.appStandbyBucket
        }.getOrNull()

        val hibernationEnabled = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@runCatching false
            // true = приложение в белом списке, гибернация ему не грозит.
            val whitelisted = appContext.packageManager.isAutoRevokeWhitelisted()
            whitelisted.not()
        }.getOrDefault(false)

        val notificationsBlocked = runCatching {
            val enabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
            enabled.not()
        }.getOrDefault(false)

        return BackgroundHealth(
            batteryOptimized = batteryOptimized,
            standbyBucket = bucketName(bucket),
            throttled = bucket != null && bucket >= STANDBY_BUCKET_RARE,
            hibernationEnabled = hibernationEnabled,
            notificationsBlocked = notificationsBlocked
        )
    }

    /** Строка для отчёта диагностики. */
    fun describe(context: Context): String {
        val health = inspect(context)
        return buildString {
            append("Battery optimized: ${health.batteryOptimized}")
            append(", standby bucket: ${health.standbyBucket}")
            append(", hibernation: ${health.hibernationEnabled}")
            append(", notifications blocked: ${health.notificationsBlocked}")
        }
    }

    // Константы UsageStatsManager объявлены только начиная с API 28, поэтому
    // держим собственные копии и сравниваем численно.
    private const val STANDBY_BUCKET_ACTIVE = 10
    private const val STANDBY_BUCKET_WORKING_SET = 20
    private const val STANDBY_BUCKET_FREQUENT = 30
    private const val STANDBY_BUCKET_RARE = 40
    private const val STANDBY_BUCKET_RESTRICTED = 45

    private fun bucketName(bucket: Int?): String = when (bucket) {
        null -> "unknown"
        STANDBY_BUCKET_ACTIVE -> "active"
        STANDBY_BUCKET_WORKING_SET -> "working_set"
        STANDBY_BUCKET_FREQUENT -> "frequent"
        STANDBY_BUCKET_RARE -> "rare"
        STANDBY_BUCKET_RESTRICTED -> "restricted"
        else -> "bucket_$bucket"
    }
}
