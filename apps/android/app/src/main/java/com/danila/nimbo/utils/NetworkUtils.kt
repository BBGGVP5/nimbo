package com.danila.nimbo.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Получение внешнего IP адреса
 */
suspend fun getExternalIpAddress(): String? = withContext(Dispatchers.IO) {
    try {
        val urls = listOf(
            "https://api.ipify.org",
            "https://ifconfig.me/ip",
            "https://icanhazip.com"
        )

        for (url in urls) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val ip = connection.inputStream.bufferedReader().readText().trim()
                if (ip.isNotEmpty()) {
                    return@withContext ip
                }
            } catch (e: Exception) {
                continue
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Определение страны по IP адресу через ipapi.co API
 */
suspend fun getCountryFromIp(ip: String?): Pair<String?, String?> {
    if (ip == null) return null to null

    // Локальные адреса
    if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.") ||
        ip.startsWith("127.") || ip.startsWith("0.0.0.0")) {
        return "Локальная сеть" to "🏠"
    }

    // Пытаемся получить информацию через API
    return try {
        val apiUrl = "https://ipapi.co/$ip/json/"
        val connection = URL(apiUrl).openConnection()
        connection.connectTimeout = 3000
        connection.readTimeout = 3000

        val response = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(response)

        val country = json.optNullableString("country_name")
        val countryCode = json.optString("country_code", "")

        if (country != null && country.isNotBlank()) {
            val flag = getFlagEmoji(countryCode)
            country to flag
        } else {
            "Неизвестно" to "🌐"
        }
    } catch (e: Exception) {
        // Fallback - определяем по первым октетам (очень приблизительно)
        val fallbackCountry = when {
            ip.startsWith("77.") || ip.startsWith("85.") || ip.startsWith("95.") ||
            ip.startsWith("176.") || ip.startsWith("178.") || ip.startsWith("185.") ||
            ip.startsWith("188.") || ip.startsWith("212.") || ip.startsWith("213.") ||
            ip.startsWith("217.") || ip.startsWith("46.") || ip.startsWith("62.") ||
            ip.startsWith("91.") || ip.startsWith("92.") || ip.startsWith("93.") ||
            ip.startsWith("94.") || ip.startsWith("109.") || ip.startsWith("176.") ->
                "Россия" to "🇷🇺"
            ip.startsWith("31.") || ip.startsWith("45.") || ip.startsWith("51.") ||
            ip.startsWith("78.") || ip.startsWith("80.") || ip.startsWith("81.") ||
            ip.startsWith("82.") || ip.startsWith("83.") || ip.startsWith("84.") ||
            ip.startsWith("86.") || ip.startsWith("87.") || ip.startsWith("88.") ||
            ip.startsWith("89.") || ip.startsWith("90.") -> "Европа" to "🇪🇺"
            ip.startsWith("8.") || ip.startsWith("17.") || ip.startsWith("23.") ||
            ip.startsWith("24.") || ip.startsWith("32.") || ip.startsWith("34.") ||
            ip.startsWith("35.") || ip.startsWith("38.") || ip.startsWith("40.") ||
            ip.startsWith("44.") || ip.startsWith("50.") || ip.startsWith("52.") ||
            ip.startsWith("54.") || ip.startsWith("63.") || ip.startsWith("64.") ||
            ip.startsWith("65.") || ip.startsWith("66.") || ip.startsWith("67.") ||
            ip.startsWith("68.") || ip.startsWith("69.") || ip.startsWith("70.") ||
            ip.startsWith("71.") || ip.startsWith("72.") || ip.startsWith("73.") ||
            ip.startsWith("74.") || ip.startsWith("75.") || ip.startsWith("76.") ||
            ip.startsWith("96.") || ip.startsWith("97.") || ip.startsWith("98.") ||
            ip.startsWith("99.") || ip.startsWith("100.") || ip.startsWith("104.") ||
            ip.startsWith("107.") || ip.startsWith("108.") || ip.startsWith("128.") ||
            ip.startsWith("129.") || ip.startsWith("130.") || ip.startsWith("131.") ||
            ip.startsWith("132.") || ip.startsWith("134.") || ip.startsWith("135.") ||
            ip.startsWith("136.") || ip.startsWith("137.") || ip.startsWith("138.") ||
            ip.startsWith("139.") || ip.startsWith("140.") || ip.startsWith("142.") ||
            ip.startsWith("143.") || ip.startsWith("144.") || ip.startsWith("146.") ||
            ip.startsWith("147.") || ip.startsWith("148.") || ip.startsWith("149.") ||
            ip.startsWith("150.") || ip.startsWith("151.") || ip.startsWith("152.") ||
            ip.startsWith("155.") || ip.startsWith("156.") || ip.startsWith("157.") ||
            ip.startsWith("158.") || ip.startsWith("159.") || ip.startsWith("160.") ||
            ip.startsWith("161.") || ip.startsWith("162.") || ip.startsWith("163.") ||
            ip.startsWith("164.") || ip.startsWith("165.") || ip.startsWith("166.") ||
            ip.startsWith("167.") || ip.startsWith("168.") || ip.startsWith("169.") ||
            ip.startsWith("170.") || ip.startsWith("172.") || ip.startsWith("173.") ||
            ip.startsWith("174.") || ip.startsWith("175.") || ip.startsWith("184.") ||
            ip.startsWith("192.") || ip.startsWith("198.") || ip.startsWith("199.") ||
            ip.startsWith("204.") || ip.startsWith("205.") || ip.startsWith("206.") ||
            ip.startsWith("207.") || ip.startsWith("208.") || ip.startsWith("209.") ||
            ip.startsWith("216.") -> "США" to "🇺🇸"
            ip.startsWith("1.") || ip.startsWith("14.") || ip.startsWith("27.") ||
            ip.startsWith("36.") || ip.startsWith("39.") || ip.startsWith("42.") ||
            ip.startsWith("43.") || ip.startsWith("49.") || ip.startsWith("58.") ||
            ip.startsWith("59.") || ip.startsWith("60.") || ip.startsWith("61.") ||
            ip.startsWith("101.") || ip.startsWith("103.") || ip.startsWith("106.") ||
            ip.startsWith("110.") || ip.startsWith("111.") || ip.startsWith("112.") ||
            ip.startsWith("113.") || ip.startsWith("114.") || ip.startsWith("115.") ||
            ip.startsWith("116.") || ip.startsWith("117.") || ip.startsWith("118.") ||
            ip.startsWith("119.") || ip.startsWith("120.") || ip.startsWith("121.") ||
            ip.startsWith("122.") || ip.startsWith("123.") || ip.startsWith("124.") ||
            ip.startsWith("125.") || ip.startsWith("126.") || ip.startsWith("133.") ||
            ip.startsWith("141.") || ip.startsWith("145.") || ip.startsWith("153.") ||
            ip.startsWith("154.") || ip.startsWith("163.") || ip.startsWith("171.") ||
            ip.startsWith("175.") || ip.startsWith("177.") || ip.startsWith("179.") ||
            ip.startsWith("180.") || ip.startsWith("181.") || ip.startsWith("182.") ||
            ip.startsWith("183.") || ip.startsWith("186.") || ip.startsWith("187.") ||
            ip.startsWith("188.") || ip.startsWith("189.") || ip.startsWith("200.") ||
            ip.startsWith("201.") || ip.startsWith("202.") || ip.startsWith("203.") ||
            ip.startsWith("210.") || ip.startsWith("211.") || ip.startsWith("218.") ||
            ip.startsWith("219.") || ip.startsWith("220.") || ip.startsWith("221.") ||
            ip.startsWith("222.") -> "Азия" to "🌏"
            else -> "Неизвестно" to "🌐"
        }
        fallbackCountry
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    val value = optString(key, "")
    return value.takeIf { it.isNotBlank() }
}

/**
 * Получение emoji флага по коду страны
 */
private fun getFlagEmoji(countryCode: String): String {
    if (countryCode.length < 2) return "🌐"

    val code = countryCode.uppercase()
    return code.map { char ->
        Character.codePointAt("$char", 0) - 0x41 + 0x1F1E6
    }.let { points ->
        String(points.toIntArray(), 0, 2)
    }
}

/**
 * Форматирование байтов в человекочитаемый формат
 */
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} МБ"
        else -> "${bytes / (1024 * 1024 * 1024)} ГБ"
    }
}

/**
 * Форматирование времени (секунды) в строку
 */
fun formatTime(sec: Int): String {
    val hours = sec / 3600
    val minutes = (sec % 3600) / 60
    val secs = sec % 60

    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, secs)
        minutes > 0 -> String.format("%02d:%02d", minutes, secs)
        else -> String.format("%02d", secs)
    }
}
