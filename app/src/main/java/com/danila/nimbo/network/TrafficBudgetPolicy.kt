package com.danila.nimbo.network

import com.danila.nimbo.model.TrafficBudget
import com.danila.nimbo.model.TrafficBudgetAction
import com.danila.nimbo.model.TrafficBudgetPeriod
import com.danila.nimbo.model.TrafficBudgetUsage
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

object TrafficBudgetPolicy {
    enum class Event { NONE, WARNING, LIMIT_REACHED }

    data class Evaluation(
        val usage: TrafficBudgetUsage,
        val event: Event,
        val action: TrafficBudgetAction,
        val fraction: Double
    )

    fun evaluate(
        budget: TrafficBudget,
        previous: TrafficBudgetUsage?,
        addedBytes: Long,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Evaluation {
        val cycleStart = cycleStart(budget.period, nowMs, zoneId)
        val base = previous?.takeIf { it.cycleStartedAtMs == cycleStart }
            ?: TrafficBudgetUsage(budget.id, 0L, cycleStart)
        val used = (base.usedBytes + addedBytes.coerceAtLeast(0)).coerceAtLeast(0)
        val fraction = if (budget.limitBytes > 0) used.toDouble() / budget.limitBytes else 1.0
        val atLimit = fraction >= 1.0
        val atWarning = fraction >= budget.warningPercent.coerceIn(1, 100) / 100.0
        val event = when {
            atLimit && !base.limitHandled -> Event.LIMIT_REACHED
            atWarning && !base.warningSent -> Event.WARNING
            else -> Event.NONE
        }
        val updated = base.copy(
            usedBytes = used,
            warningSent = base.warningSent || atWarning,
            limitHandled = base.limitHandled || atLimit
        )
        return Evaluation(updated, event, budget.action, fraction)
    }

    fun cycleStart(period: TrafficBudgetPeriod, nowMs: Long, zoneId: ZoneId): Long {
        val date = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val start = when (period) {
            TrafficBudgetPeriod.DAY -> date.toLocalDate().atStartOfDay(zoneId)
            TrafficBudgetPeriod.WEEK -> date.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zoneId)
            TrafficBudgetPeriod.MONTH -> date.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId)
        }
        return start.toInstant().toEpochMilli()
    }
}
