package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloadPolicyTest {

    @Test
    fun freshDownloadDoesNotRequestRange() {
        assertNull(UpdateDownloadPolicy.requestRangeStart(partialBytes = 0L, expectedBytes = 1_000L))
    }

    @Test
    fun partialDownloadRequestsRangeAndRequiresOnlyRemainingBytesPlusReserve() {
        assertEquals(400L, UpdateDownloadPolicy.requestRangeStart(partialBytes = 400L, expectedBytes = 1_000L))
        assertTrue(
            UpdateDownloadPolicy.hasEnoughSpace(
                availableBytes = 600L + UpdateDownloadPolicy.STORAGE_RESERVE_BYTES,
                expectedBytes = 1_000L,
                partialBytes = 400L
            )
        )
    }

    @Test
    fun fullOrOversizedPartialDoesNotRequestRange() {
        assertNull(UpdateDownloadPolicy.requestRangeStart(partialBytes = 1_000L, expectedBytes = 1_000L))
        assertNull(UpdateDownloadPolicy.requestRangeStart(partialBytes = 1_001L, expectedBytes = 1_000L))
    }

    @Test
    fun appendIsAllowedOnlyForPartialHttpResponse() {
        assertTrue(UpdateDownloadPolicy.shouldAppend(httpCode = 206, requestedStart = 400L))
        assertFalse(UpdateDownloadPolicy.shouldAppend(httpCode = 200, requestedStart = 400L))
        assertFalse(UpdateDownloadPolicy.shouldAppend(httpCode = 206, requestedStart = null))
    }

    @Test
    fun resumedResponseMustStartAtRequestedByte() {
        assertTrue(UpdateDownloadPolicy.hasMatchingContentRange("bytes 400-999/1000", 400L))
        assertFalse(UpdateDownloadPolicy.hasMatchingContentRange("bytes 0-999/1000", 400L))
        assertFalse(UpdateDownloadPolicy.hasMatchingContentRange(null, 400L))
        assertTrue(UpdateDownloadPolicy.hasMatchingContentRange(null, null))
    }

    @Test
    fun missingReserveFailsSpaceCheck() {
        assertFalse(
            UpdateDownloadPolicy.hasEnoughSpace(
                availableBytes = 599L + UpdateDownloadPolicy.STORAGE_RESERVE_BYTES,
                expectedBytes = 1_000L,
                partialBytes = 400L
            )
        )
    }

    @Test
    fun responseLengthCanRefreshStaleGitHubAssetSize() {
        assertEquals(
            30_733_000L,
            UpdateDownloadPolicy.resolvedExpectedBytes(
                metadataBytes = 30_700_000L,
                responseBytes = 30_733_000L,
                completedBytes = 0L
            )
        )
        assertEquals(
            30_700_000L,
            UpdateDownloadPolicy.resolvedExpectedBytes(
                metadataBytes = 30_700_000L,
                responseBytes = null,
                completedBytes = 0L
            )
        )
    }
}
