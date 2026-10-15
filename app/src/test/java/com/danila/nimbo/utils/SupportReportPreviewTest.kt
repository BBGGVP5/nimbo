package com.danila.nimbo.utils

import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SupportReportPreviewTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun file(name: String, text: String): File = temporary.newFile(name).apply {
        writeText(text, Charsets.UTF_8)
    }

    private fun assertSizeLimit(text: String) {
        assertTrue(text.toByteArray(Charsets.UTF_8).size <= SUPPORT_SNAPSHOT_MAX_BYTES)
        val json = JsonParser.parseString(text).asJsonObject
        assertFalse(json["available"].asBoolean)
        assertEquals("size_limit", json["reason"].asString)
    }

    @Test fun exactAsciiBoundaryIsUnchanged() {
        val text = "\"" + "a".repeat(SUPPORT_SNAPSHOT_MAX_BYTES - 2) + "\""
        assertEquals(SUPPORT_SNAPSHOT_MAX_BYTES, text.toByteArray(Charsets.UTF_8).size)
        assertEquals(text, boundedSupportSnapshot(text))
        assertEquals(text, readSupportSnapshot(file("boundary.json", text)))
    }

    @Test fun oneByteOverBoundaryBecomesCompleteJson() {
        assertSizeLimit(boundedSupportSnapshot("\"" + "a".repeat(SUPPORT_SNAPSHOT_MAX_BYTES - 1) + "\""))
    }

    @Test fun unicodeBoundaryUsesBytesAndPreservesCodePoints() {
        val text = "\"aa" + "😀".repeat((SUPPORT_SNAPSHOT_MAX_BYTES - 4) / 4) + "\""
        assertEquals(SUPPORT_SNAPSHOT_MAX_BYTES, text.toByteArray(Charsets.UTF_8).size)
        assertEquals(text, boundedSupportSnapshot(text))
        assertEquals(text, readSupportSnapshot(file("unicode.json", text)))
        assertSizeLimit(boundedSupportSnapshot(text + "я"))
        assertSizeLimit(boundedSupportSnapshot("я".repeat(SUPPORT_SNAPSHOT_MAX_BYTES)))
    }

    @Test fun legacyOversizedSnapshotIsReplacedWithoutTruncatingUnicode() {
        val oversized = file("legacy.json", "\"" + "😀".repeat(SUPPORT_SNAPSHOT_MAX_BYTES) + "\"")
        assertSizeLimit(readSupportSnapshot(oversized))
    }

    @Test fun projectedOversizedConfigStillFailsClosed() {
        val raw = "{\"proxies\":[" + List(4000) { "{\"server\":\"secret.example\",\"type\":\"vless\"}" }.joinToString(",") + "]}"
        val safe = boundedSupportSnapshot(SupportDiagnosticRedactor.config(raw))
        assertSizeLimit(safe)
        assertFalse(safe.contains("secret.example"))
    }

    @Test fun preparedPreviewsMatchEveryExportedByteAndExportRunsOffCallerThread() = runBlocking {
        val caller = Thread.currentThread()
        val files = listOf(file("report.json", "{\"versionCode\":123}"),
            file("config.json", "{\"available\":false}"), file("headers.json", "{\"test\":\"Привет 😀\"}"))
        var exports = 0
        val preview = prepareSupportReport {
            assertNotSame(caller, Thread.currentThread())
            exports++
            files
        }
        assertEquals(1, exports)
        assertEquals(files, preview.entries.map { it.file })
        preview.entries.forEach { assertArrayEquals(it.file.readBytes(), it.text.toByteArray(Charsets.UTF_8)) }
    }

    @Test fun oversizedExportFailsInsteadOfShowingDifferentContents() = runBlocking {
        val oversized = file("oversized.json", "a".repeat(SUPPORT_SNAPSHOT_MAX_BYTES + 1))
        try {
            prepareSupportReport { listOf(oversized) }
            fail("Oversized exports cannot be previewed exactly")
        } catch (_: IOException) { }
    }

    @Test fun malformedUtf8FailsInsteadOfShowingReplacementCharacters() = runBlocking {
        val malformed = temporary.newFile("malformed.json").apply { writeBytes(byteArrayOf(0xc3.toByte(), 0x28)) }
        try {
            prepareSupportReport { listOf(malformed) }
            fail("Malformed UTF-8 cannot be previewed exactly")
        } catch (_: CharacterCodingException) { }
    }

    @Test fun missingFileFailsWholePreparationWithoutReturningPartialPreview() = runBlocking {
        val valid = file("valid.json", "{}")
        var preview: SupportReportPreview? = null
        try {
            preview = prepareSupportReport { listOf(valid, File(temporary.root, "missing.json")) }
            fail("Missing file must fail preparation")
        } catch (_: IOException) { }
        assertNull(preview)
    }

    @Test fun exportFailureIsPropagated() = runBlocking {
        val failure = IOException("test failure")
        try {
            prepareSupportReport { throw failure }
            fail("Export failure must propagate")
        } catch (actual: IOException) {
            // Coroutine stack-trace recovery may copy an exception across dispatchers.
            assertEquals(failure.javaClass, actual.javaClass)
            assertEquals(failure.message, actual.message)
        }
    }

    @Test fun cancellationIsRethrown() = runBlocking {
        try {
            prepareSupportReport { throw CancellationException("cancel preparation") }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertEquals("cancel preparation", actual.message)
        }
    }

    @Test fun emptyExportCannotBecomeShareable() = runBlocking {
        try {
            prepareSupportReport { emptyList() }
            fail("Empty export must fail preparation")
        } catch (_: IllegalArgumentException) { }
    }
}
