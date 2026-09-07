package com.danila.nimbo.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

object SubscriptionLogoCache {
    // Cache original image bytes without recompression; the cap only prevents huge profile JSON.
    private const val MAX_LOGO_BYTES = 2 * 1024 * 1024

    fun displayLogo(logo: String?, cachedLogo: String?): String? {
        return cachedLogo?.trim()?.takeIf { it.isNotBlank() }
            ?: logo?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Decode a logo string (base64 data-uri, raw base64 or http(s) url) into a bitmap ready to be
     * used as a notification large icon. Preserves the original aspect ratio (no square/circle crop,
     * so wide wordmark logos aren't chopped) and only downscales when larger than [maxPx] — never
     * upscales, so a small source isn't blurred. Must be called off the main thread (http branch
     * performs blocking network IO).
     */
    fun loadLogoBitmap(logo: String, maxPx: Int = 256): Bitmap? {
        val bytes = loadLogoBytes(logo) ?: return null
        val raw = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
        val maxDim = maxOf(raw.width, raw.height)
        if (maxDim <= maxPx || maxDim <= 0) return raw
        val scale = maxPx.toFloat() / maxDim
        val w = (raw.width * scale).toInt().coerceAtLeast(1)
        val h = (raw.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(raw, w, h, true)
    }

    suspend fun prepareCachedLogo(
        logo: String?,
        previousLogo: String?,
        previousCache: String?
    ): String? = withContext(Dispatchers.IO) {
        val normalizedLogo = logo?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
        val normalizedPreviousLogo = previousLogo?.trim()?.takeIf { it.isNotBlank() }
        val normalizedPreviousCache = previousCache?.trim()?.takeIf { it.isNotBlank() }

        if (normalizedLogo == normalizedPreviousLogo && normalizedPreviousCache != null) {
            return@withContext normalizedPreviousCache
        }

        val bytes = loadLogoBytes(normalizedLogo) ?: return@withContext null
        if (bytes.isEmpty() || bytes.size > MAX_LOGO_BYTES) return@withContext null

        "data:${detectMimeType(bytes)};base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    fun loadLogoBytes(logo: String): ByteArray? {
        val normalizedLogo = logo.trim()
        if (normalizedLogo.isBlank() || isSvgLogo(normalizedLogo)) return null

        return when {
            normalizedLogo.startsWith("http://", ignoreCase = true) ||
                normalizedLogo.startsWith("https://", ignoreCase = true) -> downloadLogoBytes(normalizedLogo)

            else -> decodeEmbeddedLogoBytes(normalizedLogo)
        }?.takeIf { it.size <= MAX_LOGO_BYTES }
    }

    private fun decodeEmbeddedLogoBytes(logo: String): ByteArray? {
        return runCatching {
            val base64Part = if (logo.startsWith("data:", ignoreCase = true)) {
                if (isSvgLogo(logo)) return null
                logo.substringAfter(",", "")
            } else {
                logo
            }
            android.util.Base64.decode(base64Part.trim(), android.util.Base64.DEFAULT)
        }.getOrNull()
    }

    private fun downloadLogoBytes(url: String): ByteArray? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
            }
            try {
                if (connection.contentType?.contains("svg", ignoreCase = true) == true) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.size > MAX_LOGO_BYTES) null else bytes
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun isSvgLogo(logo: String): Boolean {
        return logo.endsWith(".svg", ignoreCase = true) ||
            logo.contains("image/svg", ignoreCase = true)
    }

    private fun detectMimeType(bytes: ByteArray): String {
        return when {
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> "image/png"

            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> "image/jpeg"

            bytes.size >= 6 &&
                bytes[0] == 0x47.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> "image/gif"

            bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() &&
                bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() &&
                bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() &&
                bytes[11] == 0x50.toByte() -> "image/webp"

            else -> "image/png"
        }
    }
}
