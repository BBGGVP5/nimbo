package com.danila.nimbo.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.MainActivity
import com.danila.nimbo.R
import com.danila.nimbo.model.UpdateInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object UpdateManager {
    private const val TAG = "UpdateManager"

    // URL для проверки обновлений через GitHub API.
    private const val GITHUB_API_URL = "https://api.github.com/repos/BBGGVP5/nimbo/releases/latest"
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1001

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    /**
     * Проверка обновлений через GitHub Releases API
     */
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates at: $GITHUB_API_URL")
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Cache-Control", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API check failed with code: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val githubRelease = Gson().fromJson(body, Map::class.java) ?: return@withContext null

            // 1. Извлекаем версию из тега
            val tagName = githubRelease["tag_name"] as? String ?: return@withContext null
            val releaseName = githubRelease["name"] as? String ?: tagName
            val releaseBody = githubRelease["body"] as? String ?: ""

            Log.d(TAG, "Found release: $releaseName (Tag: $tagName)")

            // Пытаемся найти versionCode в описании (например, "versionCode: 10")
            val manualVersionCode = Regex("versionCode:?\\s*(\\d+)").find(releaseBody)?.groupValues?.get(1)?.toIntOrNull()

            // Если в описании нет - извлекаем все числа из тега (v1.0.5 -> 105)
            val tagNumbers = Regex("\\d+").findAll(tagName).map { it.value }.joinToString("").toIntOrNull() ?: 0

            val remoteVersionCode = manualVersionCode ?: tagNumbers

            Log.d(TAG, "Local VersionCode: ${BuildConfig.VERSION_CODE}, Remote VersionCode: $remoteVersionCode")

            // 2. Проверяем, новее ли версия
            if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                Log.d(TAG, "New version available! $remoteVersionCode > ${BuildConfig.VERSION_CODE}")

                // 3. Ищем лучший APK файл в ассетах (архитектура > universal > first)
                val assets = parseAssets(githubRelease["assets"])
                val bestAsset = getBestAsset(assets)

                val downloadUrl = bestAsset?.get("browser_download_url") as? String
                    ?: return@withContext null

                val remoteFileSize = (bestAsset?.get("size") as? Number)?.toLong() ?: 0L

                UpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = releaseName,
                    downloadUrl = downloadUrl,
                    changelog = releaseBody,
                    fileSize = remoteFileSize
                )
            } else {
                Log.d(TAG, "App is up to date.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed via GitHub API", e)
            null
        }
    }

    /**
     * Выбирает лучший ассет на основе архитектуры устройства
     */
    private fun getBestAsset(assets: List<Map<String, Any?>>): Map<String, Any?>? {
        val abis = Build.SUPPORTED_ABIS
        Log.d(TAG, "Device supported ABIs: ${abis.joinToString()}")

        // 1. Поиск точного совпадения архитектуры
        for (abi in abis) {
            val match = assets.find {
                val name = (it["name"] as? String)?.lowercase() ?: ""
                name.contains(abi.lowercase().replace("-", "")) && name.endsWith(".apk")
            }
            if (match != null) {
                Log.d(TAG, "Match found for ABI $abi: ${match["name"]}")
                return match
            }
        }

        // 2. Поиск universal
        val universal = assets.find {
            val name = (it["name"] as? String)?.lowercase() ?: ""
            name.contains("universal") && name.endsWith(".apk")
        }
        if (universal != null) {
            Log.d(TAG, "Using universal APK: ${universal["name"]}")
            return universal
        }

        // 3. Первый попавшийся APK
        val firstApk = assets.find { (it["name"] as? String)?.endsWith(".apk") == true }
        Log.d(TAG, "Using fallback APK: ${firstApk?.get("name")}")
        return firstApk
    }

    private fun parseAssets(rawAssets: Any?): List<Map<String, Any?>> {
        if (rawAssets !is List<*>) return emptyList()
        return rawAssets.mapNotNull { rawItem ->
            val map = rawItem as? Map<*, *> ?: return@mapNotNull null
            map.entries
                .filter { it.key is String }
                .associate { (k, v) -> k as String to v }
        }
    }

    /**
     * Получает информацию о конкретном релизе по тегу
     */
    suspend fun getReleaseInfoForTag(tag: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/BBGGVP5/nimbo/releases/tags/$tag"
            Log.d(TAG, "Fetching release info for tag: $tag at $url")

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Fetch for tag $tag failed: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val githubRelease = Gson().fromJson(body, Map::class.java) ?: return@withContext null

            val tagName = githubRelease["tag_name"] as? String ?: tag
            val releaseName = githubRelease["name"] as? String ?: tagName
            val releaseBody = githubRelease["body"] as? String ?: ""

            UpdateInfo(
                versionCode = 0,
                versionName = releaseName,
                downloadUrl = "",
                changelog = releaseBody
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch release info for tag: $tag", e)
            null
        }
    }

    /**
     * Скачивание APK и запуск установки (с проверкой кэша)
     */
    suspend fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        if (_isDownloading.value) return@withContext

        val fileName = "Nimbo_${updateInfo.versionName.replace(" ", "_")}.apk"
        val apkFile = File(context.cacheDir, fileName)

        // 🔥 Кэш-проверка перед закачкой
        if (apkFile.exists() && apkFile.length() > 0) {
            if (updateInfo.fileSize <= 0 || apkFile.length() == updateInfo.fileSize) {
                Log.d(TAG, "APK already downloaded and size matches. Installing immediately.")
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
                return@withContext
            } else {
                Log.w(TAG, "Cached file size mismatch. Deleting and re-downloading.")
                apkFile.delete()
            }
        }

        _isDownloading.value = true
        _downloadProgress.value = 0.01f

        try {
            val request = Request.Builder().url(updateInfo.downloadUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed with code: ${response.code}")
                _isDownloading.value = false
                return@withContext
            }

            val body = response.body ?: return@withContext
            val contentLength = body.contentLength()
            val inputStream = body.byteStream()

            val outputStream = FileOutputStream(apkFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    _downloadProgress.value = totalBytesRead.toFloat() / contentLength.toFloat()
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            _isDownloading.value = false
            _downloadProgress.value = 1f

            // Запускаем установку в основном потоке
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _isDownloading.value = false
            _downloadProgress.value = null
        }
    }

    /**
     * Запуск стандартного установщика Android
     */
    fun installApk(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Для Android 8.0+ проверяем разрешение на установку из неизвестных источников
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
        }
    }

    /**
     * Показывает системное уведомление о наличии новой версии
     */
    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo) {
        val prefs = com.danila.nimbo.utils.PreferencesManager(context)
        val currentTag = updateInfo.versionName
        val now = System.currentTimeMillis()

        // Интервал между повторными уведомлениями для одной и той же версии (24 часа)
        val notifyInterval = 24 * 60 * 60 * 1000L

        if (prefs.lastUpdateNotifiedVersion == currentTag) {
            // Если мы уже уведомляли об этой версии недавно, выходим
            if (now - prefs.lastUpdateNotificationTime < notifyInterval) {
                Log.d(TAG, "Already notified about version $currentTag recently. Skipping.")
                return
            }
            // Иначе увеличиваем счетчик "настойчивости"
            prefs.updateNotificationCount += 1
        } else {
            // Новая версия - сбрасываем счетчик
            prefs.lastUpdateNotifiedVersion = currentTag
            prefs.updateNotificationCount = 1
        }

        prefs.lastUpdateNotificationTime = now
        val count = prefs.updateNotificationCount

        // Список заголовков с эмодзи (от дружелюбных к настойчивым)
        val titles = listOf(
            "Доступно обновление! 🎉",
            "Пора обновиться! 🔥",
            "Nimbo стал лучше! 🚀",
            "Ваш щит требует апгрейда ✨",
            "Не забудьте про обновление! ⚡"
        )

        val displayVersion = "v" + updateInfo.versionName.replaceFirst(Regex("^v+", RegexOption.IGNORE_CASE), "").trim()

        // Список текстов (от описательных к зазывающим)
        val contents = listOf(
            "Новая версия $displayVersion уже здесь. Нажмите, чтобы узнать больше.",
            "Вы всё еще на старой версии? В новой версии много исправлений! 🛠️",
            "Мы соскучились по обновлениям. Установите новую версию прямо сейчас! 😎",
            "Безопасность и скорость прежде всего. Переходите на ${updateInfo.versionName}! 🛡️",
            "Там столько нового! Не тяните, это займет всего минуту 💨"
        )

        val title = titles.getOrElse(count - 1) { titles.last() }
        val content = contents.getOrElse(count - 1) { contents.last() }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Создаем канал для Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Обновления приложения",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о новых версиях Nimbo"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Интент для открытия приложения на экране обновлений
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_SCREEN", "updates")
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
