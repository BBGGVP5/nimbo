package com.danila.nimbo.utils

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.danila.nimbo.NebulaGuardApplication
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Трафик за календарный день. */
data class DayTraffic(
    val date: String,
    val up: Long,
    val down: Long
) {
    val total: Long get() = up + down
}

/** Одна сессия подключения: сколько прошло и сколько прокачано. */
data class TrafficSession(
    val startMs: Long,
    val endMs: Long,
    val up: Long,
    val down: Long,
    val server: String
) {
    val total: Long get() = up + down
    val durationSeconds: Long get() = ((endMs - startMs).coerceAtLeast(0L)) / 1000L
}

/** Точка графика скорости. */
data class SpeedSample(
    val atMs: Long,
    val up: Long,
    val down: Long
)

/**
 * История трафика: то, чего на Android не хватало по сравнению с десктопом —
 * график скорости, расход по дням и журнал сессий.
 *
 * Источник цифр тот же, что и у счётчиков на главной: агрегированная статистика
 * ядра, которая приходит в [com.danila.nimbo.vpn.VpnManager.updateSpeeds].
 * Никаких оценок и достроенных данных здесь нет: если ядро молчит, история
 * просто не растёт.
 */
object TrafficHistory {

    private const val PREFS_NAME = "nimbo_traffic_history"
    private const val DAYS_KEY = "days"
    private const val SESSIONS_KEY = "sessions"

    /** Столько точек держим в графике скорости — ровно минута при тике в секунду. */
    const val SPEED_WINDOW = 60

    /** Глубина истории по дням и по сессиям. */
    const val MAX_DAYS = 31
    private const val MAX_SESSIONS = 30

    /** Чаще раза в полминуты писать JSON на диск незачем. */
    private const val FLUSH_INTERVAL_MS = 30_000L

    private val gson = Gson()
    private val lock = Any()

    private val _speedSamples = mutableStateListOf<SpeedSample>()
    val speedSamples: List<SpeedSample> get() = _speedSamples

    /** Счётчик изменений истории — по нему интерфейс перечитывает дни и сессии. */
    val revision = mutableStateOf(0)

    private var days: MutableMap<String, LongArray> = linkedMapOf()
    private var sessions: MutableList<TrafficSession> = mutableListOf()
    private var loaded = false
    private var lastFlushMs = 0L

    private var sessionStartMs = 0L
    private var sessionUp = 0L
    private var sessionDown = 0L
    private var sessionServer = ""

    // ── График скорости ───────────────────────────────────────────────────

    fun recordSpeed(up: Long, down: Long, atMs: Long = System.currentTimeMillis()) {
        _speedSamples += SpeedSample(atMs, up.coerceAtLeast(0L), down.coerceAtLeast(0L))
        while (_speedSamples.size > SPEED_WINDOW) _speedSamples.removeAt(0)
    }

    fun clearSpeedSamples() {
        _speedSamples.clear()
    }

    // ── Расход по дням ────────────────────────────────────────────────────

    fun recordTraffic(upDelta: Long, downDelta: Long, atMs: Long = System.currentTimeMillis()) {
        if (upDelta <= 0L && downDelta <= 0L) return
        synchronized(lock) {
            ensureLoaded()
            val key = dayKey(atMs)
            val bucket = days.getOrPut(key) { longArrayOf(0L, 0L) }
            bucket[0] += upDelta.coerceAtLeast(0L)
            bucket[1] += downDelta.coerceAtLeast(0L)
            sessionUp += upDelta.coerceAtLeast(0L)
            sessionDown += downDelta.coerceAtLeast(0L)
            trimDays()
            maybeFlush(atMs)
        }
    }

    /** Дни от старых к новым, включая пустые — чтобы график не «схлопывался». */
    fun recentDays(limit: Int = 7, nowMs: Long = System.currentTimeMillis()): List<DayTraffic> {
        synchronized(lock) {
            ensureLoaded()
            val calendar = Calendar.getInstance()
            val result = ArrayList<DayTraffic>(limit)
            for (offset in (limit - 1) downTo 0) {
                calendar.timeInMillis = nowMs
                calendar.add(Calendar.DAY_OF_YEAR, -offset)
                val key = dayKey(calendar.timeInMillis)
                val bucket = days[key]
                result += DayTraffic(key, bucket?.getOrNull(0) ?: 0L, bucket?.getOrNull(1) ?: 0L)
            }
            return result
        }
    }

    // ── Журнал сессий ─────────────────────────────────────────────────────

    fun startSession(server: String, atMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            ensureLoaded()
            // Если предыдущая сессия не была закрыта (процесс убили), закрываем её
            // здесь: иначе трафик двух подключений слипся бы в одну строку.
            if (sessionStartMs > 0L) finishSessionLocked(atMs)
            sessionStartMs = atMs
            sessionUp = 0L
            sessionDown = 0L
            sessionServer = server
            clearSpeedSamples()
        }
    }

    fun finishSession(atMs: Long = System.currentTimeMillis()) {
        synchronized(lock) { finishSessionLocked(atMs) }
    }

    private fun finishSessionLocked(atMs: Long) {
        if (sessionStartMs <= 0L) return
        val session = TrafficSession(
            startMs = sessionStartMs,
            endMs = atMs,
            up = sessionUp,
            down = sessionDown,
            server = sessionServer
        )
        sessionStartMs = 0L
        sessionUp = 0L
        sessionDown = 0L
        sessionServer = ""
        // Мгновенные переподключения только засоряли бы журнал.
        if (session.durationSeconds < 5L && session.total == 0L) return
        sessions.add(0, session)
        while (sessions.size > MAX_SESSIONS) sessions.removeAt(sessions.size - 1)
        flushLocked()
    }

    fun sessions(): List<TrafficSession> {
        synchronized(lock) {
            ensureLoaded()
            return sessions.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            ensureLoaded()
            days.clear()
            sessions.clear()
            sessionStartMs = 0L
            sessionUp = 0L
            sessionDown = 0L
            flushLocked()
        }
        clearSpeedSamples()
    }

    // ── Хранилище ─────────────────────────────────────────────────────────

    private fun prefs() = runCatching {
        NebulaGuardApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val store = prefs() ?: return
        runCatching {
            val daysJson = store.getString(DAYS_KEY, null)
            if (!daysJson.isNullOrBlank()) {
                val type = object : TypeToken<LinkedHashMap<String, LongArray>>() {}.type
                days = gson.fromJson<LinkedHashMap<String, LongArray>>(daysJson, type) ?: linkedMapOf()
            }
            val sessionsJson = store.getString(SESSIONS_KEY, null)
            if (!sessionsJson.isNullOrBlank()) {
                val type = object : TypeToken<MutableList<TrafficSession>>() {}.type
                sessions = gson.fromJson<MutableList<TrafficSession>>(sessionsJson, type) ?: mutableListOf()
            }
        }.onFailure {
            Log.w("TrafficHistory", "Could not read traffic history: ${it.message}")
            days = linkedMapOf()
            sessions = mutableListOf()
        }
    }

    private fun maybeFlush(nowMs: Long) {
        if (nowMs - lastFlushMs < FLUSH_INTERVAL_MS) return
        flushLocked(nowMs)
    }

    private fun flushLocked(nowMs: Long = System.currentTimeMillis()) {
        lastFlushMs = nowMs
        val store = prefs() ?: return
        runCatching {
            store.edit()
                .putString(DAYS_KEY, gson.toJson(days))
                .putString(SESSIONS_KEY, gson.toJson(sessions))
                .apply()
        }.onFailure {
            Log.w("TrafficHistory", "Could not persist traffic history: ${it.message}")
        }
        revision.value += 1
    }

    private fun trimDays() {
        if (days.size <= MAX_DAYS) return
        val ordered = days.keys.sorted()
        for (key in ordered.take(days.size - MAX_DAYS)) days.remove(key)
    }

    private fun dayKey(atMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(atMs))
}
