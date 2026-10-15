package com.danila.nimbo.shared.ui

/** Search may reveal an advanced destination but never changes the user's mode. */
fun matchesSettingsEntry(query: String, text: String, advancedOnly: Boolean, advanced: Boolean): Boolean {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    return if (tokens.isEmpty()) !advancedOnly || advanced else tokens.all { it in text.lowercase() }
}
