package com.danila.nimbo.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Менеджер для запроса runtime разрешений
 */
object PermissionManager {

    const val REQUEST_CODE_CAMERA = 1001
    const val REQUEST_CODE_NOTIFICATIONS = 1002

    /**
     * Проверка наличия разрешения на камеру
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Проверка наличия разрешения на уведомления (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        // Для Android < 13 разрешение не требуется
        return true
    }

    /**
     * Запрос разрешения на камеру
     */
    fun requestCameraPermission(activity: ComponentActivity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CODE_CAMERA
        )
    }

    /**
     * Запрос разрешения на уведомления (Android 13+)
     */
    fun requestNotificationPermission(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATIONS
            )
        }
    }

    /**
     * Проверка, нужно ли показывать rationale для камеры
     */
    fun shouldShowCameraRationale(activity: ComponentActivity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.CAMERA
        )
    }

    /**
     * Проверка, нужно ли показывать rationale для уведомлений
     */
    fun shouldShowNotificationRationale(activity: ComponentActivity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        return false
    }

    /**
     * Открытие настроек приложения для предоставления разрешений
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Проверка всех необходимых разрешений
     */
    fun hasAllPermissions(context: Context): Boolean {
        return hasCameraPermission(context) && hasNotificationPermission(context)
    }

    /**
     * Запрос всех необходимых разрешений
     */
    fun requestAllPermissions(activity: ComponentActivity) {
        if (!hasNotificationPermission(activity)) {
            requestNotificationPermission(activity)
        }
        // Камера запрашивается только при использовании QR-сканера
    }
}

