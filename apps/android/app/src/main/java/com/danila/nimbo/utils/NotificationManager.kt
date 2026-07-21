package com.danila.nimbo.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import com.danila.nimbo.MainActivity
import com.danila.nimbo.R

/**
 * Менеджер уведомлений для VPN сервиса
 */
object NotificationManager {

    const val CHANNEL_ID_VPN = "nebula_vpn_channel"
    const val CHANNEL_ID_GENERAL = "nebula_general_channel"
    const val NOTIFICATION_ID_VPN = 1001
    const val NOTIFICATION_ID_GENERAL = 1002

    /**
     * Создание каналов уведомлений
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            val isEn = PreferencesManager(context).appLanguage == "en"
            // Канал для VPN статуса
            val vpnChannel = NotificationChannel(
                CHANNEL_ID_VPN,
                if (isEn) "VPN status" else "VPN Статус",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = if (isEn) "VPN connection status notifications"
                    else "Уведомления о статусе VPN подключения"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }

            // Канал для общих уведомлений
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                if (isEn) "General" else "Общие уведомления",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = if (isEn) "General app notifications" else "Общие уведомления приложения"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(vpnChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }

    private fun formatConnectionTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
    }

    /**
     * Получение отображаемого названия сервера из имени
     * Заменяет домены на понятные названия (Автобалансер, Обход и т.д.)
     * Возвращает название без флага (флаг добавляется отдельно)
     */
    fun getServerDisplayName(serverName: String): String {
        val flag = getServerFlag(serverName)
        val nameWithoutFlag = if (flag.isNotEmpty()) {
            // Учитываем длину emoji (может быть суррогатной парой)
            serverName.substring(flag.length).trim()
        } else {
            serverName.trim()
        }

        // Если это автобалансер, мы стараемся сохранить контекст
        val isAuto = serverName.contains("автобаланс", ignoreCase = true) ||
                    serverName.contains("autobalance", ignoreCase = true) ||
                    serverName.contains("auto.Nimbo", ignoreCase = true)

        // Очистка: убираем домен и всё что после разделителя |
        var cleanName = nameWithoutFlag
            .replace(Regex("\\|.*"), "")
            .replace(Regex("\\..*"), "")
            .trim()

        if (isAuto && (cleanName.isEmpty() || cleanName.length < 3)) {
            return "Автобалансер"
        }

        return cleanName.ifEmpty { if (isAuto) "Автобалансер" else nameWithoutFlag }
    }

    /**
     * Извлечение флага из названия сервера
     */
    fun getServerFlag(serverName: String): String {
        if (serverName.isEmpty()) return ""
        
        // 1. Сначала пробуем извлечь флаг (2 региональных индикатора)
        val flagRegex = Regex("^[🇦-🇿]{2}")
        val flagMatch = flagRegex.find(serverName)
        if (flagMatch != null) return flagMatch.value
        
        // 2. Затем пробуем извлечь любой другой спецсимвол или emoji в начале
        val firstCodePoint = serverName.codePointAt(0)
        // Если это не буква, не цифра и не пробел - вероятно это emoji или иконка
        if (!Character.isLetterOrDigit(firstCodePoint) && !Character.isWhitespace(firstCodePoint)) {
            return String(Character.toChars(firstCodePoint))
        }
        
        return ""
    }

    /**
     * Форматирование скорости
     */
    private fun formatSpeed(bytesPerSecond: Long, en: Boolean): String {
        val unitB = if (en) "B/s" else "Б/с"
        val unitKb = if (en) "KB/s" else "КБ/с"
        val unitMb = if (en) "MB/s" else "МБ/с"
        val unitGb = if (en) "GB/s" else "ГБ/с"
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond $unitB"
            bytesPerSecond < 1024 * 1024 -> "%.1f %s".format(bytesPerSecond / 1024.0, unitKb)
            bytesPerSecond < 1024 * 1024 * 1024 -> "%.1f %s".format(bytesPerSecond / (1024.0 * 1024.0), unitMb)
            else -> "%.1f %s".format(bytesPerSecond / (1024.0 * 1024.0 * 1024.0), unitGb)
        }
    }

    private fun getNotificationSmallIconRes(): Int = R.drawable.icon_notification_nimbo_blue

    /**
     * Создание уведомления для VPN сервиса
     */
    fun createVpnNotification(
        context: Context,
        serverName: String,
        isConnected: Boolean = true,
        connectionTimeSeconds: Int = 0,
        profileName: String? = null,
        downSpeedBytes: Long = 0,
        upSpeedBytes: Long = 0,
        statusOverride: String? = null,
        showPauseAction: Boolean = true,
        subscriptionLogoBitmap: Bitmap? = null
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(context, com.danila.nimbo.vpn.MyVpnService::class.java).apply {
            action = com.danila.nimbo.vpn.MyVpnService.ACTION_DISCONNECT
        }

        val disconnectPendingIntent = PendingIntent.getService(
            context,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, com.danila.nimbo.vpn.MyVpnService::class.java).apply {
            action = com.danila.nimbo.vpn.MyVpnService.ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            context,
            2,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val connectionTime = formatConnectionTime(connectionTimeSeconds)

        // Получаем флаг и отображаемое название сервера
        val serverFlag = getServerFlag(serverName)
        val serverDisplayName = getServerDisplayName(serverName)
        
        // Формируем полное название с флагом (только один флаг в начале)
        val fullServerName = if (serverFlag.isNotEmpty()) {
            "$serverFlag $serverDisplayName"
        } else {
            serverDisplayName
        }

        val prefs = PreferencesManager(context)
        val isEn = prefs.appLanguage == "en"
        val downSpeed = formatSpeed(downSpeedBytes, isEn)
        val upSpeed = formatSpeed(upSpeedBytes, isEn)
        val showNotificationSpeed = prefs.showNotificationSpeed
        val showConnectionTime = prefs.showNotificationConnectionTime

        val statusText = statusOverride ?: if (isConnected) {
            if (isEn) "Connected" else "Подключено"
        } else {
            if (isEn) "Connecting..." else "Подключение..."
        }

        val notificationText = buildString {
            append(statusText)
            if (isConnected && showConnectionTime) {
                append(" • $connectionTime")
            }
            if (isConnected && showNotificationSpeed) {
                append(if (isEn) " • ↓ $downSpeed ↑ $upSpeed" else " • ↓ $downSpeed ↑ $upSpeed")
            }
        }

        val titleText = if ((isConnected || statusOverride != null) && fullServerName.isNotBlank()) {
            fullServerName
        } else {
            "Nimbo"
        }
        val contentText = statusText

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_VPN)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(getNotificationSmallIconRes())
            // SystemUI renders the left badge from this monochrome icon and notification color.
            .setColor(0xFF2869D4.toInt())
            .setColorized(false)
            // The large icon is rendered on the right by the system notification layout.
            .apply { subscriptionLogoBitmap?.let { setLargeIcon(it) } }
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setStyle(BigTextStyle().bigText(notificationText))
            .addAction(
                0,
                if (isEn) "Disconnect" else "Отключить",
                disconnectPendingIntent
            )

        if (showPauseAction) {
            builder.addAction(
                R.drawable.ic_notification_pause,
                if (isEn) "Pause" else "Пауза",
                pausePendingIntent
            )
        }

        return builder.build()
    }

    /**
     * Отмена уведомления
     */
    fun cancelNotification(context: Context, id: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(id)
    }
}
