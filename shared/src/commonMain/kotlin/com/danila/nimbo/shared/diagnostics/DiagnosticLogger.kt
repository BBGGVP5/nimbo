package com.danila.nimbo.shared.diagnostics

/** Platform implementations must redact before persisting, not only on export. */
interface DiagnosticLogger {
    fun record(event: DiagnosticEvent)
}

class InMemoryDiagnosticLogger(
    private val capacity: Int = 500
) : DiagnosticLogger {
    private val mutableEvents = mutableListOf<DiagnosticEvent>()
    val events: List<DiagnosticEvent> get() = mutableEvents.toList()

    override fun record(event: DiagnosticEvent) {
        mutableEvents += event.redacted()
        while (mutableEvents.size > capacity) mutableEvents.removeAt(0)
    }
}
