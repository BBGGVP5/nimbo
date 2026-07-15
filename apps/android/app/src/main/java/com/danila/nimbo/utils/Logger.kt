package com.danila.nimbo.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.model.LogEntry
import com.danila.nimbo.model.LogLevel
import com.danila.nimbo.vpn.VpnManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object Logger {
    private const val MAX_LOG_ENTRIES = RotatingLogStore.DEFAULT_MAX_ENTRIES
    private const val LOG_DIRECTORY = "diagnostics"

    private val stateLock = Any()
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nimbo-log-writer").apply { isDaemon = true }
    }

    @Volatile
    private var initialized = false

    @Volatile
    private var persistenceGeneration = 0L

    @Volatile
    private var store: RotatingLogStore? = null

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        synchronized(stateLock) {
            if (initialized) return
            store = RotatingLogStore(
                directory = logDirectory(appContext),
                retentionHours = PreferencesManager(appContext).diagnosticLogRetentionHours.takeIf { it > 0 }
            )
            _logEntries.value = store?.load().orEmpty()
            initialized = true
        }
        i("Logger", "Diagnostic log initialized")
    }

    fun d(tag: String, message: String) = emit(LogLevel.DEBUG, tag, message)

    fun i(tag: String, message: String) = emit(LogLevel.INFO, tag, message)

    fun w(tag: String, message: String) = emit(LogLevel.WARNING, tag, message)

    fun e(tag: String, message: String) = emit(LogLevel.ERROR, tag, message)

    fun e(tag: String, message: String, throwable: Throwable) {
        emit(LogLevel.ERROR, tag, "$message | ${buildThrowableSummary(throwable)}")
    }

    private fun emit(level: LogLevel, tag: String, message: String) {
        val safeTag = tag.take(48).ifBlank { "Nimbo" }
        val safeMessage = LogSanitizer.sanitize(message).take(2_000)
        when (level) {
            LogLevel.DEBUG -> Log.d(safeTag, safeMessage)
            LogLevel.INFO -> Log.i(safeTag, safeMessage)
            LogLevel.WARNING -> Log.w(safeTag, safeMessage)
            LogLevel.ERROR -> Log.e(safeTag, safeMessage)
        }

        // Диагностический журнал доступен и в release-сборке. Раньше DEBUG
        // попадал только в Logcat и отбрасывался до сохранения, из-за чего
        // пользователь не мог приложить его к отчёту о проблеме.
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = safeTag,
            message = safeMessage
        )
        val generation: Long
        synchronized(stateLock) {
            _logEntries.value = (_logEntries.value + entry).takeLast(MAX_LOG_ENTRIES)
            generation = persistenceGeneration
        }
        persistEntry(entry, generation)
    }

    fun clearLogs() {
        synchronized(stateLock) {
            persistenceGeneration++
            _logEntries.value = emptyList()
        }
        store?.clear()
    }

    fun saveLogs(context: Context) {
        if (!initialized) init(context)
    }

    fun updateLogRetention(context: Context) {
        if (!initialized) init(context)
        val hours = PreferencesManager(context.applicationContext).diagnosticLogRetentionHours
        synchronized(stateLock) {
            store?.setRetentionHours(hours.takeIf { it > 0 })
            _logEntries.value = store?.load().orEmpty()
            persistenceGeneration++
        }
    }

    fun getLogsAsText(entries: List<LogEntry> = _logEntries.value): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return entries.joinToString("\n") { entry ->
            val time = formatter.format(Date(entry.timestamp))
            "$time [${entry.level}] [${entry.tag}] ${entry.message}"
        }
    }

    fun buildDiagnosticReport(
        context: Context,
        entries: List<LogEntry> = _logEntries.value
    ): String {
        val appContext = context.applicationContext
        val packageInfo = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: BuildConfig.VERSION_NAME
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong()
        } ?: BuildConfig.VERSION_CODE.toLong()
        val generatedAt = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss Z",
            Locale.getDefault()
        ).format(Date())

        return buildString {
            appendLine("Nimbo diagnostic report")
            appendLine("Generated: $generatedAt")
            appendLine("App: $versionName ($versionCode)")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${LogSanitizer.sanitize("${Build.MANUFACTURER} ${Build.MODEL}")}")
            appendLine("VPN state: ${VpnManager.state.value}")
            appendLine("Recovery state: ${VpnManager.recoveryStatus.value}")
            appendLine("Entries: ${entries.size}")
            appendLine()
            append(getLogsAsText(entries))
        }
    }

    internal fun storageStats(context: Context): Pair<Long, Int> {
        return store?.stats() ?: RotatingLogStore(logDirectory(context)).stats()
    }

    private fun persistEntry(entry: LogEntry, generation: Long) {
        val activeStore = store ?: return
        ioExecutor.execute {
            if (generation != persistenceGeneration) return@execute
            if (generation != persistenceGeneration) return@execute
            runCatching {
                activeStore.append(entry)
            }.onFailure {
                Log.e("Logger", "Unable to persist diagnostic event: ${it::class.java.simpleName}")
            }
        }
    }

    private fun logDirectory(context: Context): File {
        return File(context.filesDir, LOG_DIRECTORY).apply { mkdirs() }
    }

    private fun buildThrowableSummary(throwable: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 3) {
            val type = current::class.java.simpleName.ifBlank { "Exception" }
            val message = current.message?.takeIf(String::isNotBlank) ?: "no details"
            parts += "$type: ${LogSanitizer.sanitize(message)}"
            current = current.cause
            depth++
        }
        return parts.joinToString(" <- ")
    }
}
