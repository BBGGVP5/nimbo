package com.danila.nimbo.network

object NetworkEventSanitizer {
    private val uuid = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")
    private val token = Regex("(?i)(token|key|secret|password|uuid)=([^&\\s]+)")
    private val urlQuery = Regex("(https?://[^?\\s]+)\\?[^\\s]+")

    fun sanitize(value: String?): String? = value
        ?.replace(urlQuery, "$1?[hidden]")
        ?.replace(token, "$1=[hidden]")
        ?.replace(uuid, "[uuid]")
        ?.take(500)
}
