package com.danila.nimbo.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Результат честной проверки узла: запрос к независимым сервисам, прошедший
 * через сам узел.
 *
 * [latencyMs] — задержка такой сквозной проверки, [ok] — признали ли узел
 * рабочим (см. [AutoSelectionPolicy.isUsable]).
 */
data class VerifiedLatency(
    val latencyMs: Int,
    val ok: Boolean,
    val checkedAtMs: Long
)

/**
 * Хранит результаты сквозных проверок узлов.
 *
 * Зачем: TCP-пинг меряет лишь то, что кто-то на пути принял соединение. У узлов
 * вида `google.com:456` (обходы на подменном адресе) это верно всегда — цифра
 * получается красивой, а туннель через такой узел не поднимается. Поэтому
 * результат реальной проверки хранится отдельно и имеет приоритет над TCP.
 *
 * Живёт в том же процессе, что и UI (у сервиса нет android:process), поэтому
 * StateFlow видят обе стороны; на диск пишем, чтобы проверка не терялась между
 * запусками.
 */
object VerifiedLatencyStore {
    private const val TAG = "VerifiedLatency"
    private const val PREFS_NAME = "nimbo_verified_latency"
    private const val KEY_ENTRIES = "entries"

    /** Сколько результат считается свежим: дальше узел мог и измениться. */
    const val FRESH_WINDOW_MS = 6L * 60L * 60L * 1000L

    private val _entries = MutableStateFlow<Map<String, VerifiedLatency>>(emptyMap())
    val entries: StateFlow<Map<String, VerifiedLatency>> = _entries.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _entries.value = runCatching { readFromDisk() }.getOrElse {
            Log.w(TAG, "Не удалось прочитать сохранённые проверки: ${it.message}")
            emptyMap()
        }
    }

    /**
     * Записывает исход проверки. Неудача сохраняется наравне с успехом: знание
     * «этот узел не работает» и есть то, чего не даёт TCP-пинг.
     */
    fun record(measurementKey: String, latencyMs: Int, ok: Boolean) {
        val key = measurementKey.trim()
        if (key.isEmpty()) return
        val entry = VerifiedLatency(
            latencyMs = if (ok) latencyMs.coerceAtLeast(1) else -1,
            ok = ok,
            checkedAtMs = System.currentTimeMillis()
        )
        _entries.value = _entries.value + (key to entry)
        Log.i(TAG, "Проверка узла: ok=$ok latency=${entry.latencyMs}ms")
        runCatching { writeToDisk(_entries.value) }
            .onFailure { Log.w(TAG, "Не удалось сохранить проверки: ${it.message}") }
    }

    fun freshFor(
        measurementKey: String,
        nowMs: Long = System.currentTimeMillis()
    ): VerifiedLatency? = _entries.value[measurementKey]
        ?.takeIf { nowMs - it.checkedAtMs <= FRESH_WINDOW_MS }

    fun clear() {
        _entries.value = emptyMap()
        runCatching { writeToDisk(emptyMap()) }
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readFromDisk(): Map<String, VerifiedLatency> {
        val raw = prefs()?.getString(KEY_ENTRIES, null)?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val json = JSONObject(raw)
        val result = HashMap<String, VerifiedLatency>()
        for (key in json.keys()) {
            val item = json.optJSONObject(key) ?: continue
            result[key] = VerifiedLatency(
                latencyMs = item.optInt("latency", -1),
                ok = item.optBoolean("ok", false),
                checkedAtMs = item.optLong("at", 0L)
            )
        }
        return result
    }

    private fun writeToDisk(entries: Map<String, VerifiedLatency>) {
        val json = JSONObject()
        entries.forEach { (key, value) ->
            json.put(
                key,
                JSONObject()
                    .put("latency", value.latencyMs)
                    .put("ok", value.ok)
                    .put("at", value.checkedAtMs)
            )
        }
        prefs()?.edit()?.putString(KEY_ENTRIES, json.toString())?.apply()
    }
}
