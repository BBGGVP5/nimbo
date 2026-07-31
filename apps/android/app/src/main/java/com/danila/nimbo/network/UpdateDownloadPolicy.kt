package com.danila.nimbo.network

internal object UpdateDownloadPolicy {
    const val STORAGE_RESERVE_BYTES = 32L * 1024L * 1024L

    fun requestRangeStart(partialBytes: Long, expectedBytes: Long): Long? =
        partialBytes.takeIf { it > 0L && (expectedBytes <= 0L || it < expectedBytes) }

    fun shouldAppend(httpCode: Int, requestedStart: Long?): Boolean =
        requestedStart != null && httpCode == 206

    fun hasMatchingContentRange(contentRange: String?, requestedStart: Long?): Boolean {
        if (requestedStart == null) return true
        val start = Regex("""^bytes\s+(\d+)-\d+/(?:\d+|\*)$""", RegexOption.IGNORE_CASE)
            .matchEntire(contentRange?.trim().orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        return start == requestedStart
    }

    fun requiredFreeBytes(expectedBytes: Long, partialBytes: Long): Long {
        val remaining = if (expectedBytes > 0L) {
            (expectedBytes - partialBytes).coerceAtLeast(0L)
        } else {
            0L
        }
        return remaining + STORAGE_RESERVE_BYTES
    }

    fun hasEnoughSpace(
        availableBytes: Long,
        expectedBytes: Long,
        partialBytes: Long
    ): Boolean = availableBytes >= requiredFreeBytes(expectedBytes, partialBytes)
}
