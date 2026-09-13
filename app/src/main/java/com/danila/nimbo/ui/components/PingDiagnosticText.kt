package com.danila.nimbo.ui.components

import com.danila.nimbo.network.NimboPingSummary
import com.danila.nimbo.network.NimboPingPhase

internal fun pingDiagnosticLastStage(summary: NimboPingSummary): NimboPingPhase =
    summary.lastChildPhase ?: summary.stages.lastOrNull {
        it.phase !in setOf(NimboPingPhase.FINISHED, NimboPingPhase.CLEANUP_STARTED, NimboPingPhase.CLEANUP_JOINED)
    }?.phase ?: summary.phase

/** Export only the closed enum/timing schema. Never add profile, URL, log or exception text. */
internal fun pingDiagnosticText(summary: NimboPingSummary): String = buildString {
    appendLine("Nimbo Ping diagnostic v1 (latest probe; not a node identifier)")
    appendLine("outcome=${summary.outcome.name}")
    appendLine("lastChildPhase=${summary.lastChildPhase?.name ?: "NONE"}")
    appendLine("elapsedMs=${summary.elapsedMs}; remainingMs=${summary.remainingMs}")
    summary.stages.takeLast(32).forEach {
        appendLine("${it.phase.name}: elapsedMs=${it.elapsedMs}; remainingMs=${it.remainingMs}")
    }
}
