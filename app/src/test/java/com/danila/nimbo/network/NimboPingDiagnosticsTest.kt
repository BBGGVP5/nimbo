package com.danila.nimbo.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NimboPingDiagnosticsTest {
    private class Fixture {
        var now = 0L
        var latest: NimboPingSummary? = null
        val trace = NimboPingTrace(100, { now }) { latest = it }
    }

    @Test fun `history is capped at32 with independent snapshot lists`() {
        val f = Fixture()
        f.trace.record(NimboPingPhase.QUEUE_WAIT)
        val first = f.latest!!
        repeat(100) { f.now++; f.trace.record(NimboPingPhase.WAIT_RESULT) }
        assertEquals(32, f.latest!!.stages.size)
        assertEquals(1, first.stages.size)
        assertEquals(69L, f.latest!!.stages.first().elapsedMs)
    }

    @Test fun `wire accepts only known child enum integers`() {
        val f = Fixture()
        listOf(-1, Int.MAX_VALUE, NimboPingPhase.FINISHED.ordinal, NimboPingPhase.BIND_CONNECTED.ordinal).forEach(f.trace::child)
        assertNull(f.latest)
        f.trace.child(NimboPingPhase.CORE_STARTED.ordinal)
        assertEquals(NimboPingPhase.CORE_STARTED, f.latest!!.lastChildPhase)
    }

    @Test fun `child death retains last confirmed phase through joined cleanup`() {
        val f = Fixture()
        f.trace.child(NimboPingPhase.CORE_START_REQUESTED.ordinal)
        f.trace.record(NimboPingPhase.PROCESS_LOST)
        f.trace.measurementEnded()
        f.trace.record(NimboPingPhase.CLEANUP_STARTED)
        f.now = 150
        f.trace.record(NimboPingPhase.CLEANUP_JOINED)
        f.trace.finish(-1)
        assertEquals(NimboPingPhase.CORE_START_REQUESTED, f.latest!!.lastChildPhase)
        assertEquals(NimboPingOutcome.PROCESS_LOST, f.latest!!.outcome)
    }

    @Test fun `timeout retains confirmed GET phase and ignores teardown death and late child messages`() {
        val f = Fixture()
        f.trace.child(NimboPingPhase.TARGET_GET_STARTED.ordinal)
        f.now = 100
        f.trace.measurementEnded()
        f.trace.record(NimboPingPhase.PROCESS_LOST)
        f.trace.child(NimboPingPhase.RUN_RECEIVED.ordinal)
        f.trace.record(NimboPingPhase.RESULT_RECEIVED)
        f.trace.finish(-1)
        assertEquals(NimboPingPhase.TARGET_GET_STARTED, f.latest!!.lastChildPhase)
        assertEquals(NimboPingOutcome.DEADLINE_EXCEEDED, f.latest!!.outcome)
    }

    @Test fun `early unavailable is not reclassified by cleanup crossing deadline`() {
        val f = Fixture()
        f.now = 20
        f.trace.measurementEnded()
        f.now = 150
        f.trace.measurementEnded()
        f.trace.finish(-1)
        assertEquals(NimboPingOutcome.UNAVAILABLE, f.latest!!.outcome)
    }

    @Test fun `zero result succeeds even when cleanup crosses deadline`() {
        val f = Fixture()
        f.now = 90
        f.trace.record(NimboPingPhase.RESULT_RECEIVED)
        f.trace.measurementEnded()
        f.now = 150
        f.trace.record(NimboPingPhase.CLEANUP_JOINED)
        f.trace.finish(0)
        assertEquals(NimboPingOutcome.SUCCESS, f.latest!!.outcome)
        assertEquals(0L, f.latest!!.remainingMs)
    }

    @Test fun `queue expiry has no invented child phase`() {
        val f = Fixture()
        f.trace.record(NimboPingPhase.QUEUE_WAIT)
        f.now = 100
        f.trace.measurementEnded()
        f.trace.finish(-1)
        assertNull(f.latest!!.lastChildPhase)
        assertEquals(NimboPingOutcome.DEADLINE_EXCEEDED, f.latest!!.outcome)
    }

    @Test fun `closed request ignores all late messages`() {
        val f = Fixture()
        f.trace.finish(-1)
        val finished = f.latest
        f.trace.child(NimboPingPhase.CORE_STARTED.ordinal)
        f.trace.record(NimboPingPhase.RESULT_RECEIVED)
        f.trace.finish(20)
        assertSame(finished, f.latest)
    }

    @Test fun `superseded request cannot overwrite newest global summary`() {
        val old = NimboPingDiagnostics.begin(100) { 0 }
        old.record(NimboPingPhase.QUEUE_WAIT)
        val newest = NimboPingDiagnostics.begin(100) { 0 }
        newest.record(NimboPingPhase.BIND_REQUEST)
        val expected = NimboPingDiagnostics.latest.value
        old.child(NimboPingPhase.TARGET_GET_STARTED.ordinal)
        old.finish(12)
        assertSame(expected, NimboPingDiagnostics.latest.value)
    }

    @Test fun `child delivery failure does not change success failure or measurement call count`() {
        var sends = 0
        val sender = NimboPingStageSender { sends++; error("synthetic sink failure") }
        var measurements = 0
        fun probe(value: Int): Int {
            sender.stage(NimboPingPhase.CORE_STARTED)
            sender.stage(NimboPingPhase.TARGET_GET_STARTED)
            measurements++
            return value
        }
        assertEquals(0, probe(0))
        assertEquals(420, probe(420))
        assertEquals(-1, probe(-1))
        assertEquals(3, measurements)
        assertEquals(6, sends)
    }

    @Test fun `child diagnostic delivery failure cannot swallow probe cancellation`() {
        val sender = NimboPingStageSender { throw IllegalStateException() }
        val cancelled = CancellationException("synthetic cancellation")
        val thrown = runCatching {
            sender.stage(NimboPingPhase.TARGET_GET_STARTED)
            throw cancelled
        }.exceptionOrNull()
        assertSame(cancelled, thrown)
    }

    @Test fun `actual readiness measurement path does not retry failed GET when diagnostic sink fails`() = runBlocking {
        val sender = NimboPingStageSender { throw IllegalStateException() }
        var gets = 0
        val value = NimboPingReadiness.measureOnce(awaitReady = {
            sender.stage(NimboPingPhase.BALANCER_WAIT)
            true
        }, measure = {
            sender.stage(NimboPingPhase.TARGET_GET_STARTED)
            gets++
            -1
        })
        assertEquals(-1, value)
        assertEquals(1, gets)
    }

    @Test fun `bounded child delivery has no retry even for throwing sink`() {
        var sends = 0
        val sender = NimboPingStageSender { sends++; throw IllegalStateException() }
        repeat(100) { sender.stage(NimboPingPhase.CORE_STARTED) }
        sender.stage(NimboPingPhase.FINISHED)
        assertEquals(32, sends)
    }

    @Test fun `broken parent publication does not escape into probe result`() {
        val trace = NimboPingTrace(100, { 0 }) { throw AssertionError("synthetic sink failure") }
        fun probe(): Int {
            trace.record(NimboPingPhase.WAIT_RESULT)
            trace.child(NimboPingPhase.TARGET_GET_STARTED.ordinal)
            trace.record(NimboPingPhase.RESULT_RECEIVED)
            trace.measurementEnded()
            trace.finish(17)
            return 17
        }
        assertEquals(17, probe())
    }

    @Test fun `cancellation is explicit even after received success`() {
        val f = Fixture()
        f.trace.record(NimboPingPhase.RESULT_RECEIVED)
        f.trace.measurementEnded()
        f.trace.finish(20, cancelled = true)
        assertEquals(NimboPingOutcome.CANCELLED, f.latest!!.outcome)
    }

    @Test fun `timing never exposes negative values`() {
        val f = Fixture()
        f.now = -10
        f.trace.record(NimboPingPhase.QUEUE_WAIT)
        assertEquals(0L, f.latest!!.elapsedMs)
        f.now = 200
        f.trace.record(NimboPingPhase.WAIT_RESULT)
        assertEquals(0L, f.latest!!.remainingMs)
    }

    @Test fun `public payload has only enum timing and bounded stage-list fields`() {
        assertEquals(setOf("phase", "lastChildPhase", "outcome", "elapsedMs", "remainingMs", "stages"),
            NimboPingSummary::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
        assertEquals(setOf("phase", "elapsedMs", "remainingMs"),
            NimboPingStage::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
        assertFalse(NimboPingSummary::class.java.declaredFields.any { it.type == String::class.java })
    }
}
