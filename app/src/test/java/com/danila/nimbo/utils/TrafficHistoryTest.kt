package com.danila.nimbo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Хранилище истории в юнит-тесте работает без Android: доступ к SharedPreferences
 * обёрнут в runCatching и молча отваливается, поэтому проверяем ровно логику —
 * окно графика скорости и заполнение пропущенных дней.
 */
class TrafficHistoryTest {

    @Before
    fun resetSamples() {
        TrafficHistory.clearSpeedSamples()
    }

    @Test
    fun `speed window keeps only the last minute of samples`() {
        repeat(TrafficHistory.SPEED_WINDOW + 20) { index ->
            TrafficHistory.recordSpeed(up = index.toLong(), down = index.toLong() * 2, atMs = index.toLong())
        }

        val samples = TrafficHistory.speedSamples
        assertEquals(TrafficHistory.SPEED_WINDOW, samples.size)
        // Остаться должен хвост, а не начало: график показывает последнюю минуту.
        assertEquals((TrafficHistory.SPEED_WINDOW + 19).toLong(), samples.last().up)
    }

    @Test
    fun `negative speed never reaches the chart`() {
        TrafficHistory.recordSpeed(up = -100L, down = -1L)
        val sample = TrafficHistory.speedSamples.single()
        assertEquals(0L, sample.up)
        assertEquals(0L, sample.down)
    }

    @Test
    fun `recent days always returns a full window in chronological order`() {
        val now = TimeUnit.DAYS.toMillis(20_000)
        val days = TrafficHistory.recentDays(limit = 7, nowMs = now)

        assertEquals(7, days.size)
        assertEquals(days.map { it.date }.sorted(), days.map { it.date })
        // Дни без трафика остаются в списке нулями — иначе график «схлопывался» бы.
        assertTrue(days.all { it.total == 0L })
    }

    @Test
    fun `day totals sum upload and download`() {
        val day = DayTraffic(date = "2026-08-23", up = 1_000L, down = 4_000L)
        assertEquals(5_000L, day.total)
    }

    @Test
    fun `session duration is measured in whole seconds and never negative`() {
        val session = TrafficSession(
            startMs = 10_000L,
            endMs = 25_500L,
            up = 1L,
            down = 2L,
            server = "Amsterdam 02"
        )
        assertEquals(15L, session.durationSeconds)
        assertEquals(3L, session.total)

        val broken = session.copy(startMs = 30_000L, endMs = 10_000L)
        assertEquals(0L, broken.durationSeconds)
    }
}
