package com.danila.nimbo.ui.components

import com.danila.nimbo.network.*
import org.junit.Assert.*
import org.junit.Test

class PingDiagnosticTextTest {
    @Test fun `export contains only closed vocabulary and timing with bounded history`() {
        val stages = List(40) { NimboPingStage(NimboPingPhase.TARGET_GET_STARTED, it.toLong(), 0) }
        val summary = NimboPingSummary(NimboPingPhase.FINISHED, NimboPingPhase.TARGET_GET_STARTED,
            NimboPingOutcome.UNAVAILABLE, 100, 0, stages)
        val text = pingDiagnosticText(summary)
        assertTrue(text.contains("outcome=UNAVAILABLE"))
        assertTrue(text.contains("lastChildPhase=TARGET_GET_STARTED"))
        assertEquals(32, text.lineSequence().count { it.startsWith("TARGET_GET_STARTED:") })
        assertFalse(text.contains("http"))
        assertFalse(text.contains("elapsedMs=0;"))
    }
    @Test fun `prebind failure has no invented child phase`() {
        val summary = NimboPingSummary(NimboPingPhase.FINISHED, null,
            NimboPingOutcome.DEADLINE_EXCEEDED, 3000, 0, listOf(
                NimboPingStage(NimboPingPhase.BIND_REQUEST, 0, 3000),
                NimboPingStage(NimboPingPhase.CLEANUP_JOINED, 3000, 0),
                NimboPingStage(NimboPingPhase.FINISHED, 3000, 0)
            ))
        assertTrue(pingDiagnosticText(summary).contains("lastChildPhase=NONE"))
        assertEquals(NimboPingPhase.BIND_REQUEST, pingDiagnosticLastStage(summary))
    }
}
