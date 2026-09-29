package com.danila.nimbo.ui.components

/** Preferences store whole seconds, not displayed latency milliseconds. Invalid drafts never save. */
internal fun parsePingTimeoutSeconds(draft: String): Int? {
    val value = draft.trim()
    if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
    return value.toIntOrNull()?.takeIf { it in 1..10 }
}
