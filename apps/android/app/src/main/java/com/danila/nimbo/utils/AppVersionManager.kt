package com.danila.nimbo.utils

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Менеджер для получения информации о версии приложения
 */
object AppVersionManager {

    /**
     * User-Agent для запросов подписки.
     * Формат: Nimbo/1.0.0/Android
     */
    fun getUserAgent(context: Context): String {
        val appVersion = getVersionName(context)
        return "Nimbo/$appVersion/Android"
    }

    /**
     * Получение уникального идентификатора устройства (HWID)
     */
    fun getHWID(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.trim()

        // ANDROID_ID стабилен для приложения и не сбрасывается при переустановке.
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return androidId.lowercase()
        }

        // Fallback для редких устройств, где ANDROID_ID недоступен/некорректен.
        return PreferencesManager(context).hardwareId
    }

    fun getDeviceModel(): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL

        val marketName = getSystemProperty("ro.product.marketname")
            ?: getSystemProperty("ro.product.vendor.marketname")
            ?: getSystemProperty("ro.product.system.marketname")
            ?: getSystemProperty("ro.product.odm.marketname")

        val humanReadable = when {
            !marketName.isNullOrBlank() -> marketName
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }.replaceFirstChar { it.uppercase() }

        return if (!marketName.isNullOrBlank() && !model.equals(marketName, ignoreCase = true)) {
            "$humanReadable ($model)"
        } else {
            humanReadable
        }
    }

    fun getOSVersion(): String {
        return android.os.Build.VERSION.RELEASE
    }

    /**
     * Получение имени версии (например, "1.0")
     */
    fun getVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }

    /**
     * Получение кода версии (например, 1)
     */
    fun getVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode
        } catch (e: PackageManager.NameNotFoundException) {
            1
        }
    }

    private fun getSystemProperty(key: String): String? {
        return runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("get", String::class.java, String::class.java)
            val value = get.invoke(null, key, "") as? String
            value?.trim().takeUnless { it.isNullOrBlank() }
        }.getOrNull()
    }
}
