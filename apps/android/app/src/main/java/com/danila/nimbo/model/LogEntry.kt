package com.danila.nimbo.model

data class LogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    val formattedTime: String
        get() = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

