package com.danila.nimbo.shared.diagnostics

import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosticLevel { DEBUG, INFO, WARNING, ERROR }

@Serializable
enum class DiagnosticStage {
    APP,
    PERMISSION,
    CONFIG,
    CORE_LOAD,
    TUNNEL_START,
    ROUTE,
    DNS,
    PROBE,
    NETWORK_CHANGE,
    RECOVERY,
    STOP
}

@Serializable
data class DiagnosticEvent(
    val timestampEpochMs: Long,
    val level: DiagnosticLevel,
    val stage: DiagnosticStage,
    val code: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap()
) {
    fun redacted(): DiagnosticEvent = copy(
        message = DiagnosticRedactor.redact(message),
        metadata = metadata.mapValues { (_, value) -> DiagnosticRedactor.redact(value) }
    )
}
