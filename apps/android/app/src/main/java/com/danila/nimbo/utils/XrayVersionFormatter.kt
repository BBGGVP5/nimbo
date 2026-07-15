package com.danila.nimbo.utils

object XrayVersionFormatter {
    private val versionRegex = Regex("""\d+(?:\.\d+){1,4}""")

    fun format(raw: String?): String {
        val normalized = raw
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return "—"

        return versionRegex.find(normalized)?.value ?: "—"
    }
}
