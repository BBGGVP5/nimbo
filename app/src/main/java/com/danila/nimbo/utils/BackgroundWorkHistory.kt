package com.danila.nimbo.utils

import android.content.Context
import kotlinx.coroutines.CancellationException

/** A bounded local record: no URLs, device names or exception text. */
object BackgroundWorkHistory {
    data class Entry(val task: String, val started: Long, val finished: Long, val outcome: String)
    val tasks = listOf("updates", "subscriptions", "sync")

    suspend fun <T> track(context: Context, task: String, block: suspend () -> T): T {
        require(task in tasks)
        val prefs = context.getSharedPreferences("background_work_history", Context.MODE_PRIVATE)
        val started = System.currentTimeMillis()
        prefs.edit().putLong("$task.started", started).putLong("$task.finished", 0)
            .putString("$task.outcome", "running").apply()
        var outcome = "finished"
        try { return block() }
        catch (cancelled: CancellationException) { outcome = "cancelled"; throw cancelled }
        catch (error: Throwable) { outcome = "failed"; throw error }
        finally {
            // A replaced worker must not overwrite the newer run's record.
            if (prefs.getLong("$task.started", 0) == started) {
                prefs.edit().putLong("$task.finished", System.currentTimeMillis())
                    .putString("$task.outcome", outcome).apply()
            }
        }
    }

    fun read(context: Context): List<Entry> {
        val prefs = context.getSharedPreferences("background_work_history", Context.MODE_PRIVATE)
        return tasks.map { Entry(it, prefs.getLong("$it.started", 0),
            prefs.getLong("$it.finished", 0), prefs.getString("$it.outcome", "unknown") ?: "unknown") }
    }
}
