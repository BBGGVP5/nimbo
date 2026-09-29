package com.danila.nimbo.shared.updates

object ReleaseDefaults {
    const val VERSION = "1.3.0-beta.1"
    const val UPDATE_CHANNEL = "beta"

    /** Beta for new installs; never replace an explicitly saved channel. */
    fun updateChannel(savedValue: String?): String =
        when (savedValue?.trim()?.lowercase()) {
            "stable" -> "stable"
            "beta" -> "beta"
            else -> UPDATE_CHANNEL
        }
}
