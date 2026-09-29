package com.danila.nimbo.network

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NimboPingSweepTest {
    @Test fun `full subscription measures all 92 configs with distinct identity and bounded work`() = runBlocking {
        val targets = (0 until 92).map { NimboPingTarget("same-host-row-$it", "route-$it") }
        val measured = mutableListOf<String>()
        val published = linkedMapOf<String, Int>()
        var inFlight = 0
        var maximum = 0
        NimboPingSweep.run(targets, measure = { config ->
            inFlight++; maximum = maxOf(maximum, inFlight)
            measured += config
            yield()
            inFlight--
            config.removePrefix("route-").toInt()
        }, publish = { key, value -> published[key] = value })
        assertEquals(targets.map { it.config }, measured)
        assertEquals(92, published.size)
        assertEquals(0, published["same-host-row-0"])
        assertEquals(73, published["same-host-row-73"])
        assertEquals(1, maximum)
    }

    @Test fun `unsupported node never probes active or direct route and later nodes still run`() = runBlocking {
        val published = linkedMapOf<String, Int>()
        val measured = mutableListOf<String>()
        NimboPingSweep.run(listOf(NimboPingTarget("awg", null), NimboPingTarget("vless", "vless-config")),
            { measured += it; 17 }, { key, result -> published[key] = result })
        assertEquals(listOf("vless-config"), measured)
        assertEquals(mapOf("awg" to -1, "vless" to 17), published)
    }

    @Test fun `cancellation stops queued work and discards even a noncancellable late success`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        var requests = 0
        val sweep = launch {
            NimboPingSweep.run(listOf(NimboPingTarget("first", "one"), NimboPingTarget("second", "two")), {
                requests++
                started.complete(Unit)
                withContext(NonCancellable) { delay(25); 0 }
            }, { key, _ -> published += key })
        }
        started.await()
        sweep.cancelAndJoin()
        assertEquals(1, requests)
        assertTrue(published.isEmpty())
    }
}
