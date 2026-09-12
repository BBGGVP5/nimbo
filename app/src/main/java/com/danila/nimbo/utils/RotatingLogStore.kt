package com.danila.nimbo.utils

import com.danila.nimbo.model.LogEntry
import com.google.gson.Gson
import java.io.File

internal class RotatingLogStore(
    private val directory: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    retentionHours: Int? = null
) {
    private val lock = Any()
    private val gson = Gson()
    private val currentFile = File(directory, CURRENT_FILE_NAME)
    private val archiveFile = File(directory, ARCHIVE_FILE_NAME)
    private var retentionMs: Long? = retentionHours?.let(::hoursToMs)

    fun append(entry: LogEntry) {
        synchronized(lock) {
            directory.mkdirs()
            val encoded = gson.toJson(entry) + "\n"
            val encodedBytes = encoded.toByteArray(Charsets.UTF_8).size
            if (
                currentFile.exists() &&
                currentFile.length() > 0 &&
                currentFile.length() + encodedBytes > maxFileBytes
            ) {
                archiveFile.delete()
                if (!currentFile.renameTo(archiveFile)) {
                    currentFile.delete()
                }
            }
            currentFile.appendText(encoded, Charsets.UTF_8)
            pruneLocked()
        }
    }

    fun load(): List<LogEntry> {
        return synchronized(lock) {
            pruneLocked()
            readEntriesLocked()
                .filterByRetention()
                .sortedBy(LogEntry::timestamp)
                .takeLast(maxEntries)
        }
    }

    fun setRetentionHours(hours: Int?) {
        synchronized(lock) {
            retentionMs = hours?.let(::hoursToMs)
            pruneLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            currentFile.delete()
            archiveFile.delete()
        }
    }

    fun stats(): Pair<Long, Int> {
        return synchronized(lock) {
            val files = listOf(currentFile, archiveFile).filter(File::exists)
            files.sumOf(File::length) to files.size
        }
    }

    private fun pruneLocked() {
        val retention = retentionMs ?: return
        val cutoff = System.currentTimeMillis() - retention
        val retained = readEntriesLocked()
            .filter { it.timestamp >= cutoff }
            .sortedBy(LogEntry::timestamp)
            .takeLast(maxEntries)

        archiveFile.delete()
        if (retained.isEmpty()) {
            currentFile.delete()
        } else {
            directory.mkdirs()
            currentFile.writeText(
                retained.joinToString(separator = "\n", postfix = "\n") { gson.toJson(it) },
                Charsets.UTF_8
            )
        }
    }

    private fun readEntriesLocked(): List<LogEntry> {
        return listOf(archiveFile, currentFile)
            .filter(File::exists)
            .flatMap { file ->
                runCatching {
                    file.useLines { lines ->
                        lines.mapNotNull { line ->
                            runCatching {
                                gson.fromJson(line, LogEntry::class.java)
                            }.getOrNull()
                        }.toList()
                    }
                }.getOrDefault(emptyList())
            }
    }

    private fun List<LogEntry>.filterByRetention(): List<LogEntry> {
        val retention = retentionMs ?: return this
        val cutoff = System.currentTimeMillis() - retention
        return filter { it.timestamp >= cutoff }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 1_000
        const val DEFAULT_MAX_FILE_BYTES = 512L * 1024L
        private const val CURRENT_FILE_NAME = "nimbo-events.jsonl"
        private const val ARCHIVE_FILE_NAME = "nimbo-events.1.jsonl"

        private fun hoursToMs(hours: Int): Long {
            return hours.coerceAtLeast(1).toLong() * 60L * 60L * 1000L
        }
    }
}
