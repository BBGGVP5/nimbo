package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Test

class PingManagerTest {

    @Test
    fun aggregateTcpSamples_dropsSlowOutlierAndUsesMedian() {
        val result = PingManager.aggregateTcpSamples(listOf(31, 30, 250, 32))

        assertEquals(31, result)
    }

    @Test
    fun aggregateTcpSamples_averagesTwoMiddleSamplesForEvenCount() {
        val result = PingManager.aggregateTcpSamples(listOf(40, 44))

        assertEquals(42, result)
    }

    @Test
    fun aggregateTcpSamples_returnsMinusOneWhenNoSuccessfulSamples() {
        val result = PingManager.aggregateTcpSamples(emptyList())

        assertEquals(-1, result)
    }
}
