package com.danila.nimbo.utils

import com.danila.nimbo.model.LogEntry
import com.danila.nimbo.model.LogLevel
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotatingLogStoreTest {

    @Test
    fun reload_restoresLatestEntriesInOrder() {
        val directory = Files.createTempDirectory("nimbo-logs").toFile()
        val store = RotatingLogStore(directory, maxEntries = 3)

        repeat(5) { index ->
            store.append(entry(index))
        }

        val restored = RotatingLogStore(directory, maxEntries = 3).load()

        assertEquals(listOf("event-2", "event-3", "event-4"), restored.map(LogEntry::message))
    }

    @Test
    fun rotation_keepsOnlyCurrentAndSingleArchive() {
        val directory = Files.createTempDirectory("nimbo-logs").toFile()
        val store = RotatingLogStore(directory, maxFileBytes = 220, maxEntries = 100)
        val payload = "x".repeat(80)

        repeat(20) { index ->
            store.append(entry(index, "event-$index-$payload"))
        }

        val (bytes, fileCount) = store.stats()
        assertTrue(fileCount in 1..2)
        assertTrue(bytes < 1_000)
        assertTrue(store.load().isNotEmpty())
    }

    @Test
    fun clear_removesPersistedLogs() {
        val directory = Files.createTempDirectory("nimbo-logs").toFile()
        val store = RotatingLogStore(directory)
        store.append(entry(1))

        store.clear()

        assertTrue(store.load().isEmpty())
        assertEquals(0L to 0, store.stats())
    }

    private fun entry(index: Int, message: String = "event-$index") = LogEntry(
        timestamp = index.toLong(),
        level = LogLevel.INFO,
        tag = "Test",
        message = message
    )
}
