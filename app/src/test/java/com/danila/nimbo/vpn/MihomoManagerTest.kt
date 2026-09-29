package com.danila.nimbo.vpn

import android.os.Build
import com.danila.nimbo.mihomo.MihomoException
import com.google.gson.JsonParser
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class MihomoManagerTest {
    @Test fun startupBudgetNeverOutlivesTheConnectionCycle() {
        assertEquals(0L, mihomoStartupBudgetMs(-1))
        assertEquals(0L, mihomoStartupBudgetMs(0))
        assertEquals(3_000L, mihomoStartupBudgetMs(3_000))
        assertEquals(25_000L, mihomoStartupBudgetMs(60_000))
    }

    @Test fun startupFailureReportsOnlyFixedStageAndCode() {
        assertEquals("Mihomo [NATIVE_START]: TUN_START_FAILED",
            mihomoStartFailure(MihomoStartStage.NATIVE_START, MihomoException("TUN_START_FAILED")))
        val privateError = IllegalStateException("user:secret@private.example:443")
        assertEquals("Mihomo [RESTORE_SELECTION]: START_FAILED",
            mihomoStartFailure(MihomoStartStage.RESTORE_SELECTION, privateError))
    }

    @Test fun admitsOnlyExactSupportedSettingsAndDeliberateBypassCoverage() {
        assertTrue(MihomoOptions().conflicts().isEmpty())
        assertTrue(MihomoOptions(ipType = "dual").conflicts().isEmpty())
        assertTrue(MihomoOptions(perAppMode = 1).conflicts().isEmpty())
        assertTrue(MihomoOptions(perAppMode = 2).conflicts().isEmpty())
        assertFalse(MihomoOptions(perAppMode = -1).conflicts().isEmpty())
        assertEquals(Build.VERSION.SDK_INT < 33, MihomoOptions(directLan = true).conflicts().isNotEmpty())
    }

    @Test fun incompatiblePreferencesAreNotSilentlyIgnored() {
        listOf(
            MihomoOptions(ipType = "ipv6"), MihomoOptions(dnsMode = "local"),
            MihomoOptions(dnsMode = "hybrid"), MihomoOptions(routing = true),
            MihomoOptions(modules = true), MihomoOptions(blockUdp = true),
            MihomoOptions(automaticSelection = true),
            MihomoOptions(hotspot = true), MihomoOptions(killSwitch = true)
        ).forEach { assertFalse(it.toString(), it.conflicts().isEmpty()) }
        if (Build.VERSION.SDK_INT < 33) assertFalse(MihomoOptions(directLan = true).conflicts().isEmpty())
    }

    @Test fun originalFdClosesOnlyAfterGenerationBoundNativeStopAndOnlyOnce() {
        val events = mutableListOf<String>()
        val lease = MihomoFdLease(Closeable { events += "close" }).apply { generation = 42 }
        lease.stopAndClose { events += "stop:$it" }
        lease.stopAndClose { fail("Second stop must be a no-op") }
        assertEquals(listOf("stop:42", "close"), events)
    }

    @Test fun stopFailureRetainsFdAndGenerationForRetry() {
        var closed = false
        val lease = MihomoFdLease(Closeable { closed = true }).apply { generation = 17 }
        try {
            lease.stopAndClose { throw IllegalStateException("STALE_GENERATION") }
            fail("Stop failure must propagate")
        } catch (_: IllegalStateException) { }
        assertFalse(closed)
        lease.stopAndClose { assertEquals(17L, it) }
        assertTrue(closed)
    }

    @Test fun nativeReadinessRequiresRunningOwnedTunAndIsNotAConnectivityMeasurement() {
        fun ready(json: String) = mihomoReady(JsonParser.parseString(json).asJsonObject)
        assertTrue(ready("""{"state":"running","networkOwner":"android-vpn","tunReady":true}"""))
        listOf(
            """{"state":"failed","networkOwner":"android-vpn","tunReady":true}""",
            """{"state":"running","networkOwner":"android-vpn","tunReady":false}""",
            """{"state":"running","networkOwner":"desktop","tunReady":true}""",
            """{"state":"running"}""", "{}"
        ).forEach { assertFalse(it, ready(it)) }
    }

    @Test fun queuedStopDoesNotBlockCallerAndNextEngineWaitsForNativeJoin() = runBlocking {
        val lifecycle = MihomoEngineLifecycle()
        val stopEntered = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val started = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val stop = lifecycle.stop({ cancelled.set(true) }) {
            stopEntered.countDown()
            check(releaseStop.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue(cancelled.get())
            assertTrue(stopEntered.await(5, TimeUnit.SECONDS))
            val start = async { lifecycle.start { started.set(true) } }
            yield()
            assertFalse(started.get())
            releaseStop.countDown()
            stop.await()
            start.await()
            assertTrue(started.get())
        } finally { releaseStop.countDown() }
    }

    @Test fun failedStopBlocksOtherEngineUntilExplicitCleanupRetry() = runBlocking {
        val lifecycle = MihomoEngineLifecycle()
        val closed = AtomicBoolean(false)
        val lease = MihomoFdLease(Closeable { closed.set(true) }).apply { generation = 9 }
        val failed = lifecycle.stop({}) { lease.stopAndClose { error("native stop failed") } }
        try { failed.await(); fail("Stop must fail") } catch (_: IllegalStateException) { }
        try {
            lifecycle.start { fail("Must not start after failed stop") }
            fail("Start must propagate the stop failure")
        } catch (_: IllegalStateException) { }
        assertFalse(closed.get())
        lifecycle.stop({}) { lease.stopAndClose { assertEquals(9L, it) } }.await()
        assertTrue(lifecycle.start { closed.get() })
    }
}
