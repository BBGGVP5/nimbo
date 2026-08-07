package com.danila.nimbo.ui.screens

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object UpdateUiText {
    const val APK_SIZE_MISMATCH = "APK_SIZE_MISMATCH"

    fun fileSize(bytes: Long, language: String, decimals: Int = 1): String {
        val english = language.equals("en", ignoreCase = true)
        val locale = if (english) Locale.US else Locale.forLanguageTag("ru-RU")
        val safeBytes = bytes.coerceAtLeast(0L)
        val useMegabytes = safeBytes == 0L || safeBytes >= 1024L * 1024L
        val divisor = if (useMegabytes) 1024.0 * 1024.0 else 1024.0
        val unit = when {
            english && useMegabytes -> "MB"
            english -> "KB"
            useMegabytes -> "МБ"
            else -> "КБ"
        }
        val formatter = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            isGroupingUsed = false
        }
        return "${formatter.format(safeBytes / divisor)} $unit"
    }

    fun versionLabel(value: String, language: String): String {
        val clean = value
            .trim()
            .replaceFirst(Regex("^v+", RegexOption.IGNORE_CASE), "")
        if (clean.isBlank()) return "Nimbo"

        val betaNumber = Regex("(?i)(?:[-._]?)beta(?:[-._]?)(\\d+)")
            .find(clean)
            ?.groupValues
            ?.getOrNull(1)
        val stablePart = clean
            .replace(Regex("(?i)(?:[-._]?)beta(?:[-._]?)(\\d+)?"), "")
            .trim('-', '.', '_', ' ')
        val channel = when {
            betaNumber != null -> " Beta $betaNumber"
            clean.contains("beta", ignoreCase = true) -> " Beta"
            else -> ""
        }
        return "v${stablePart.ifBlank { clean }}$channel"
    }

    fun releaseDate(
        value: String?,
        language: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String? {
        val instant = runCatching { Instant.parse(value?.trim().orEmpty()) }.getOrNull() ?: return null
        val english = language.equals("en", ignoreCase = true)
        val locale = if (english) Locale.US else Locale.forLanguageTag("ru-RU")
        val pattern = if (english) "MMMM d, yyyy · h:mm a" else "d MMMM yyyy · HH:mm"
        return DateTimeFormatter.ofPattern(pattern, locale)
            .withZone(zoneId)
            .format(instant)
    }

    fun error(raw: String, language: String): String {
        val english = language.equals("en", ignoreCase = true)
        if (raw.startsWith("Недостаточно свободного места. Освободите не менее ")) {
            val amount = Regex("(\\d+)\\s*МБ").find(raw)?.groupValues?.getOrNull(1)
            return if (english) {
                "Not enough free space. Free at least ${amount ?: "the required amount of"} MB"
            } else raw
        }
        if (raw.startsWith("Download failed: HTTP ")) {
            return if (english) raw else raw.replace("Download failed", "Ошибка загрузки")
        }
        return when (raw) {
            APK_SIZE_MISMATCH,
            "Размер APK не совпадает с данными GitHub" -> if (english) {
                "The APK size does not match GitHub data"
            } else {
                "Размер APK не совпадает с данными GitHub"
            }

            "Загрузка обновлений разрешена только по Wi‑Fi" -> if (english) {
                "Updates can only be downloaded over Wi-Fi"
            } else raw

            "Загруженный APK пуст" -> if (english) "The downloaded APK is empty" else raw
            "GitHub returned an empty APK" -> if (english) raw else "GitHub вернул пустой APK"
            "Сервер обновлений вернул неверный диапазон файла" -> if (english) {
                "The update server returned an invalid file range"
            } else raw
            "SHA-256 APK не совпадает с цифровым отпечатком релиза" -> if (english) {
                "The APK SHA-256 does not match the release fingerprint"
            } else raw

            "Загруженный файл не является корректным APK" -> if (english) {
                "The downloaded file is not a valid APK"
            } else raw
            "APK выпущен для другого приложения" -> if (english) {
                "The APK belongs to a different app"
            } else raw
            "Версия внутри APK не совпадает с релизом" -> if (english) {
                "The APK version does not match the release"
            } else raw
            "versionCode внутри APK не совпадает с релизом" -> if (english) {
                "The APK versionCode does not match the release"
            } else raw
            "Android не разрешает откат на более старый versionCode" -> if (english) {
                "Android does not allow downgrading to an older versionCode"
            } else raw

            "Сертификат подписи APK не совпадает с установленным Nimbo" -> if (english) {
                "The APK signing certificate does not match the installed Nimbo app"
            } else raw

            else -> raw
        }
    }
}
