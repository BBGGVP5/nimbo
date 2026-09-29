package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MihomoPingResultTest {
    @Test fun onlyStableDiagnosticCodesCrossTheProcessBoundary() {
        assertEquals(MihomoPingFailure.HTTP_STATUS, MihomoPingFailure.fromWire("HTTP_STATUS"))
        assertEquals(MihomoPingFailure.GET_FAILED, MihomoPingFailure.fromWire("GET_FAILED"))
        assertEquals(MihomoPingFailure.REALITY_AUTH_FAILED, MihomoPingFailure.fromWire("REALITY_AUTH_FAILED"))
        assertEquals(MihomoPingFailure.DNS_FAILED, MihomoPingFailure.fromWire("DNS_FAILED"))
        assertNull(MihomoPingFailure.fromWire("user:password@private.example"))
        assertNull(MihomoPingFailure.fromWire("DELAY_FAILED: proxy secret"))
        assertNull(MihomoPingFailure.fromWire(null))
    }
}
