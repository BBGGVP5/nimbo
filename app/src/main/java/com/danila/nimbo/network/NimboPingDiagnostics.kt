package com.danila.nimbo.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fixed vocabulary only: never add node, URL, config or exception text to this schema. */
enum class NimboPingPhase(val isChild: Boolean = false) {
    VALIDATING, QUEUE_WAIT, QUEUE_ACQUIRED, PROCESS_RETIRING,
    BIND_REQUEST, BIND_CONNECTED, PIPE_SENT, WAIT_RESULT,
    RUN_RECEIVED(true), CONFIG_READ_STARTED(true), CONFIG_READ(true),
    NETWORK_SELECT_STARTED(true), PHYSICAL_NETWORK_SELECTED(true), PROCESS_NETWORK_BOUND(true),
    ROUTE_VERIFY_STARTED(true), ROUTE_VERIFIED(true), ASSETS_PREPARE_STARTED(true), ASSETS_READY(true),
    CORE_START_REQUESTED(true), CORE_STARTED(true), BALANCER_WAIT(true), TARGET_GET_STARTED(true),
    RESULT_RECEIVED, PROCESS_LOST, CLEANUP_STARTED, CLEANUP_JOINED, FINISHED;

    internal companion object {
        fun childFromWire(value: Int): NimboPingPhase? = entries.getOrNull(value)?.takeIf { it.isChild }
    }
}

enum class NimboPingOutcome { IN_PROGRESS, SUCCESS, UNAVAILABLE, DEADLINE_EXCEEDED, PROCESS_LOST, CANCELLED }

data class NimboPingStage(val phase: NimboPingPhase, val elapsedMs: Long, val remainingMs: Long)

/** Latest request only, not associated with any node or subscription. Times are monotonic milliseconds. */
data class NimboPingSummary(
    val phase: NimboPingPhase,
    val lastChildPhase: NimboPingPhase?,
    val outcome: NimboPingOutcome,
    val elapsedMs: Long,
    val remainingMs: Long,
    val stages: List<NimboPingStage>,
)

@androidx.annotation.Keep // Stable APK marker for the phone-safe stage diagnostics correction.
object NimboPingDiagnostics {
    private val state = MutableStateFlow<NimboPingSummary?>(null)
    val latest: StateFlow<NimboPingSummary?> = state.asStateFlow()
    private var active: Any? = null

    @Synchronized internal fun begin(deadline: Long, now: () -> Long): NimboPingTrace {
        val token = Any()
        active = token
        return NimboPingTrace(deadline, now) { summary ->
            synchronized(this) { if (active === token) state.value = summary }
        }.also { it.record(NimboPingPhase.VALIDATING) }
    }
}

/** Best effort only. A broken diagnostic sink must not change the probe's result or cancellation. */
internal fun pingDiagnosticOnly(action: () -> Unit) { runCatching(action) }

internal class NimboPingTrace(
    private val deadline: Long,
    private val now: () -> Long,
    private val publish: (NimboPingSummary) -> Unit,
) {
    private val started = now()
    private val stages = ArrayDeque<NimboPingStage>()
    private var lastChild: NimboPingPhase? = null
    private var closed = false
    private var receivedResult = false
    private var processLost = false
    private var timedOut = false
    private var ended = false

    @Synchronized fun record(phase: NimboPingPhase) = pingDiagnosticOnly {
        if (!closed && (!ended || phase == NimboPingPhase.CLEANUP_STARTED || phase == NimboPingPhase.CLEANUP_JOINED)) {
            if (phase.isChild) lastChild = phase
            if (phase == NimboPingPhase.RESULT_RECEIVED) receivedResult = true
            if (phase == NimboPingPhase.PROCESS_LOST && !receivedResult) processLost = true
            emit(phase, NimboPingOutcome.IN_PROGRESS)
        }
    }

    fun child(value: Int) { NimboPingPhase.childFromWire(value)?.let(::record) }

    /** Called before cleanup: cleanup duration cannot make an earlier failure look like a timeout. */
    @Synchronized fun measurementEnded() = pingDiagnosticOnly {
        if (!ended) {
            ended = true
            timedOut = !receivedResult && now() >= deadline
        }
    }

    @Synchronized fun finish(value: Int, cancelled: Boolean = false) = pingDiagnosticOnly {
        if (!closed) {
            closed = true
            val outcome = when {
                cancelled -> NimboPingOutcome.CANCELLED
                value >= 0 -> NimboPingOutcome.SUCCESS
                processLost -> NimboPingOutcome.PROCESS_LOST
                timedOut -> NimboPingOutcome.DEADLINE_EXCEEDED
                else -> NimboPingOutcome.UNAVAILABLE
            }
            emit(NimboPingPhase.FINISHED, outcome)
        }
    }

    private fun emit(phase: NimboPingPhase, outcome: NimboPingOutcome) {
        val time = now()
        val event = NimboPingStage(phase, (time - started).coerceAtLeast(0), (deadline - time).coerceAtLeast(0))
        if (stages.size == 32) stages.removeFirst()
        stages.addLast(event)
        publish(NimboPingSummary(phase, lastChild, outcome, event.elapsedMs, event.remainingMs, stages.toList()))
    }
}

/** Sends at most32 one-way, integer-only messages. Delivery is never required for RESULT. */
internal class NimboPingStageSender(private val send: (Int) -> Unit) {
    private var count = 0
    fun stage(phase: NimboPingPhase) = pingDiagnosticOnly {
        if (phase.isChild && count < 32) { count++; send(phase.ordinal) }
    }
}
