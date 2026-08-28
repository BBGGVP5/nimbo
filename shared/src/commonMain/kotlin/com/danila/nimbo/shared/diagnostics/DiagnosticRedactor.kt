package com.danila.nimbo.shared.diagnostics

object DiagnosticRedactor {
    private val uuid = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")
    private val ipv4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val bearer = Regex("(?i)(bearer\\s+)[a-z0-9._~+/=-]+")
    private val sensitiveQuery = Regex("(?i)([?&](?:token|key|secret|password|uuid|id)=)[^&#\\s]+")
    private val urlCredentials = Regex("(?i)(https?://)[^/@\\s]+@")
    private val subscriptionPath = Regex("(?i)(/sub(?:scription)?/)[^/?#\\s]+")

    fun redact(value: String): String = value
        .replace(urlCredentials, "$1***@")
        .replace(sensitiveQuery, "$1***")
        .replace(subscriptionPath, "$1***")
        .replace(bearer, "$1***")
        .replace(uuid, "***-uuid")
        .replace(ipv4, "***.***.***.***")
}
