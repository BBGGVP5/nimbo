package com.danila.nimbo.network

import com.danila.nimbo.model.TrafficBudget
import com.danila.nimbo.model.TrafficBudgetAction
import com.danila.nimbo.model.TrafficBudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TrafficBudgetPolicyTest {
    @Test
    fun emitsWarningAndLimitOnlyOncePerCycle() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 8, 5, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val budget = TrafficBudget(
            id = "monthly",
            name = "10 MB",
            limitBytes = 10_000,
            period = TrafficBudgetPeriod.MONTH,
            action = TrafficBudgetAction.DISCONNECT,
            warningPercent = 80
        )
        val warning = TrafficBudgetPolicy.evaluate(budget, null, 8_000, now, zone)
        assertEquals(TrafficBudgetPolicy.Event.WARNING, warning.event)
        val limit = TrafficBudgetPolicy.evaluate(budget, warning.usage, 2_000, now, zone)
        assertEquals(TrafficBudgetPolicy.Event.LIMIT_REACHED, limit.event)
        val repeated = TrafficBudgetPolicy.evaluate(budget, limit.usage, 100, now, zone)
        assertEquals(TrafficBudgetPolicy.Event.NONE, repeated.event)
    }
}
