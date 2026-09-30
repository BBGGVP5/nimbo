package com.danila.nimbo.utils

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Read only, safe with no VPN running. These fields contain no notification text or node IDs. */
internal data class VpnPillSnapshot(
    val availability: VpnPillAvailability,
    val postedPromoted: Boolean?,
    val postedCompatible: Boolean?
)

internal object VpnLiveUpdatePlatform {
    fun snapshot(context: Context): VpnPillSnapshot {
        val supported = Build.VERSION.SDK_INT >= 36
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val permission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val notificationsAllowed = permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
        val importance = manager.getNotificationChannel(NotificationManager.CHANNEL_ID_VPN)?.importance
            ?: android.app.NotificationManager.IMPORTANCE_LOW
        val promotion = if (supported) runCatching { manager.canPostPromotedNotifications() }.getOrNull() else false
        val current = if (supported) runCatching {
            manager.activeNotifications.firstOrNull { it.id == NotificationManager.NOTIFICATION_ID_VPN }?.notification
        }.getOrNull() else null
        return VpnPillSnapshot(
            vpnPillAvailability(supported, PreferencesManager(context).vpnLiveUpdateEnabled,
                notificationsAllowed, importance, promotion),
            current?.let { it.flags and Notification.FLAG_PROMOTED_ONGOING != 0 },
            current?.let { runCatching { it.hasPromotableCharacteristics() }.getOrNull() }
        )
    }

    /** User-click only. Do not change channel importance or enable any permission ourselves. */
    fun openSettings(context: Context, availability: VpnPillAvailability): Boolean {
        val action = when (availability) {
            VpnPillAvailability.CHANNEL_BLOCKED, VpnPillAvailability.CHANNEL_MINIMIZED -> Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            VpnPillAvailability.NOTIFICATIONS_BLOCKED, VpnPillAvailability.UNSUPPORTED -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            else -> if (Build.VERSION.SDK_INT >= 36) Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS
                else Settings.ACTION_APP_NOTIFICATION_SETTINGS
        }
        val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationManager.CHANNEL_ID_VPN)
        return runCatching { context.startActivity(intent); true }.recoverCatching {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            true
        }.getOrDefault(false)
    }
}
