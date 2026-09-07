package com.danila.nimbo.shared.updates

object ReleaseNotesText {
    private val platforms = setOf("android", "андроид", "ios", "windows", "виндовс", "linux", "линукс", "desktop", "десктоп")
    private val connectors = setOf("на", "для", "в", "и", "and", "on", "for", "in", "app", "приложение", "приложении")
    private val introductions = listOf("что нового", "что изменилось", "изменения", "what s new", "what is new", "what changed", "changes", "changelog")

    /** Removes only wrapper titles; feature headings and fenced examples are content. */
    fun withoutPlatformHeading(body: String): String {
        var fence: String? = null
        return body.lineSequence().filter { line ->
            val trimmed = line.trim()
            val marker = when {
                trimmed.startsWith("```") -> trimmed.takeWhile { it == '`' }
                trimmed.startsWith("~~~") -> trimmed.takeWhile { it == '~' }
                else -> null
            }
            if (marker != null) {
                val current = fence
                if (current == null) fence = marker
                else if (marker.first() == current.first() && marker.length >= current.length) fence = null
                true
            } else fence != null || !isPlatformHeading(trimmed)
        }.joinToString("\n").trim()
    }

    private fun isPlatformHeading(line: String): Boolean {
        // Do not mistake list items for wrapper titles, including "- Android".
        if (Regex("^(?:[-+*] |[0-9]+[.)] )").containsMatchIn(line)) return false
        val normalized = line.replace(Regex("<[^>]+>"), "").lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .trim().replace(Regex("\\s+"), " ")
        val prefix = introductions.firstOrNull { normalized == it || normalized.startsWith("$it ") }
        val tail = if (prefix == null) normalized else normalized.removePrefix(prefix).trim()
        if (tail.isEmpty()) return prefix != null
        val words = tail.split(' ')
        return words.any { it in platforms } && words.all { it in platforms || it in connectors }
    }

    /** A release without this tagged platform must not display another platform's notes. */
    fun forPlatform(body: String, platform: String): String {
        val marker = Regex("<!--\\s*nimbo:${Regex.escape(platform)}:start\\s*-->(.*?)<!--\\s*nimbo:${Regex.escape(platform)}:end\\s*-->",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val selected = marker.find(body)?.groupValues?.get(1)
        val hasPlatformSections = Regex("<!--\\s*nimbo:[a-z]+:start\\s*-->", RegexOption.IGNORE_CASE).containsMatchIn(body)
        if (selected == null && hasPlatformSections) return ""
        return withoutPlatformHeading((selected ?: body).replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), ""))
    }
}
