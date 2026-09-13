package com.danila.nimbo.network

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import java.util.PriorityQueue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class NimboPingDeadlineTest {
    /** Deterministic virtual clock without adding production or build dependencies. */
    private class Clock : CoroutineDispatcher(), Delay {
        var now = 0L
        private var serial = 0L
        private class Event(val at: Long, val order: Long, val task: Runnable) { var cancelled = false }
        private val events = PriorityQueue<Event>(compareBy<Event> { it.at }.thenBy { it.order })
        private fun schedule(ms: Long, task: Runnable): Event = Event(now + ms, serial++, task).also(events::add)
        override fun dispatch(context: CoroutineContext, block: Runnable) { schedule(0, block) }
        override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
            val event = schedule(timeMillis, Runnable { if (continuation.isActive) continuation.resume(Unit) })
            continuation.invokeOnCancellation { event.cancelled = true }
        }
        override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
            val event = schedule(timeMillis, block)
            return DisposableHandle { event.cancelled = true }
        }
        fun drain() {
            var steps = 0
            while (events.isNotEmpty()) {
                check(++steps < 10_000)
                val event = events.remove()
                if (!event.cancelled) { now = event.at; event.task.run() }
            }
        }
    }
    private fun virtual(block: suspend CoroutineScope.(Clock) -> Unit) {
        val clock = Clock()
        val task = CoroutineScope(clock).async { block(clock) }
        clock.drain()
        assertTrue("virtual test must finish", task.isCompleted)
        task.getCompleted()
    }

    @Test fun `predeadline success survives cleanup crossing deadline under held lock`() = virtual { clock ->
        val worker = Mutex()
        val result = NimboPingDeadline.withWorker(worker, 100, { clock.now }) {
            NimboPingDeadline.measureThenCleanup(100, { clock.now }, { delay(90); 17 }, {
                assertTrue(worker.isLocked); delay(40); assertTrue(worker.isLocked)
            })
        }
        assertEquals(17, result); assertEquals(130L, clock.now); assertFalse(worker.isLocked)
    }

    @Test fun `true measurement timeout stays unavailable and still joins cleanup`() = virtual { clock ->
        val worker = Mutex(); var cleaned = false
        val result = NimboPingDeadline.withWorker(worker, 100, { clock.now }) {
            NimboPingDeadline.measureThenCleanup(100, { clock.now }, { delay(110); 17 }, { delay(40); cleaned = true })
        }
        assertEquals(-1, result); assertEquals(140L, clock.now); assertTrue(cleaned); assertFalse(worker.isLocked)
    }

    @Test fun `queued worker expires without starting or releasing another owners lock`() = virtual { clock ->
        val worker = Mutex(locked = true); var started = false
        val waiter = async { NimboPingDeadline.withWorker(worker, 50, { clock.now }) { started = true; 17 } }
        delay(60)
        assertEquals(-1, waiter.await()); assertFalse(started); assertTrue(worker.isLocked)
        worker.unlock(); assertFalse(worker.isLocked)
    }

    @Test fun `cancelled queued caller does not leak or steal worker lock`() = virtual { clock ->
        val worker = Mutex(locked = true)
        val waiter = async { NimboPingDeadline.withWorker(worker, 100, { clock.now }) { fail("must not start"); 17 } }
        delay(10); waiter.cancelAndJoin()
        assertTrue(worker.isLocked); worker.unlock()
        assertEquals(1, NimboPingDeadline.withWorker(worker, 100, { clock.now }) { 1 })
        assertFalse(worker.isLocked)
    }

    @Test fun `next probe cannot start until prior cleanup has joined`() = virtual { clock ->
        val worker = Mutex(); var cleanupFinished = -1L; var nextStarted = -1L
        val first = async {
            NimboPingDeadline.withWorker(worker, 100, { clock.now }) {
                NimboPingDeadline.measureThenCleanup(100, { clock.now }, { delay(90); 17 }, { delay(40); cleanupFinished = clock.now })
            }
        }
        delay(95)
        val second = async { NimboPingDeadline.withWorker(worker, 500, { clock.now }) { nextStarted = clock.now; 18 } }
        assertEquals(17, first.await()); assertEquals(18, second.await())
        assertEquals(130L, cleanupFinished); assertEquals(cleanupFinished, nextStarted); assertFalse(worker.isLocked)
    }

    @Test fun `external cancellation during cleanup suppresses publication and releases after join`() = virtual { clock ->
        val worker = Mutex(); val entered = CompletableDeferred<Unit>(); var cleaned = false; var published = false
        val first = launch {
            NimboPingDeadline.withWorker(worker, 100, { clock.now }) {
                NimboPingDeadline.measureThenCleanup(100, { clock.now }, { delay(90); 17 }, {
                    entered.complete(Unit); delay(40); cleaned = true
                })
            }
            published = true
        }
        entered.await(); delay(5); first.cancelAndJoin()
        assertEquals(130L, clock.now); assertTrue(cleaned); assertFalse(published); assertFalse(worker.isLocked)
        assertEquals(18, NimboPingDeadline.withWorker(worker, 200, { clock.now }) { 18 })
    }

    @Test fun `time spent queued is not added back to measurement budget`() = virtual { clock ->
        val worker = Mutex(locked = true)
        val waiter = async {
            NimboPingDeadline.withWorker(worker, 100, { clock.now }) {
                NimboPingDeadline.measureThenCleanup(100, { clock.now }, { delay(50); 17 }, {})
            }
        }
        delay(60); worker.unlock()
        assertEquals(-1, waiter.await()); assertEquals(100L, clock.now); assertFalse(worker.isLocked)
    }

    @Test fun `exception and cancellation at acquired handoff never leak lock`() = virtual { clock ->
        val worker = Mutex(); var cleaned = false
        try {
            NimboPingDeadline.withWorker(worker, 100, { clock.now }) {
                NimboPingDeadline.measureThenCleanup(100, { clock.now }, { error("synthetic") }, { delay(10); cleaned = true })
            }
            fail("exception expected")
        } catch (_: IllegalStateException) { }
        assertTrue(cleaned); assertFalse(worker.isLocked)
        val cancelled = launch {
            NimboPingDeadline.withWorker(worker, 100, { clock.now }) {
                currentCoroutineContext().cancel()
                currentCoroutineContext().ensureActive()
                17
            }
        }
        cancelled.join(); assertTrue(cancelled.isCancelled); assertFalse(worker.isLocked)
    }
}
