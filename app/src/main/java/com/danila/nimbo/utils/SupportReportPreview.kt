package com.danila.nimbo.utils

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal const val SUPPORT_SNAPSHOT_MAX_BYTES = 32 * 1024
private const val SIZE_LIMIT_SNAPSHOT = "{\"available\":false,\"reason\":\"size_limit\"}"

/** Replace the whole document, never truncate JSON or a UTF-8 code point. */
internal fun boundedSupportSnapshot(value: String): String =
    if (value.toByteArray(Charsets.UTF_8).size <= SUPPORT_SNAPSHOT_MAX_BYTES) value
    else SIZE_LIMIT_SNAPSHOT

/** Read at most limit + 1 bytes, including snapshots saved by older app versions. */
internal fun readSupportSnapshot(file: File, replaceOversize: Boolean = true): String {
    val bytes = ByteArray(SUPPORT_SNAPSHOT_MAX_BYTES + 1)
    val size = file.inputStream().use { input ->
        var total = 0
        while (total < bytes.size) {
            val read = input.read(bytes, total, bytes.size - total)
            if (read < 0) break
            total += read
        }
        total
    }
    if (size > SUPPORT_SNAPSHOT_MAX_BYTES) {
        if (replaceOversize) return SIZE_LIMIT_SNAPSHOT
        throw IOException("Support report exceeds size limit")
    }
    // Fail rather than silently replacing malformed UTF-8 in an exact preview.
    return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, size)).toString()
}

internal data class SupportReportFile(val file: File, val text: String)
internal data class SupportReportPreview(val entries: List<SupportReportFile>)

/** Export and all reads run on IO. Errors/cancellation never return a partial preview. */
internal suspend fun prepareSupportReport(export: () -> List<File>): SupportReportPreview =
    withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val files = export()
        require(files.isNotEmpty()) { "Empty support report" }
        SupportReportPreview(files.map { file ->
            currentCoroutineContext().ensureActive()
            SupportReportFile(file, readSupportSnapshot(file, replaceOversize = false))
        })
    }
