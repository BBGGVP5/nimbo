package com.danila.nimbo.shared.updates

object ReleaseDefaults {
    const val VERSION = "1.2.0"
    const val UPDATE_CHANNEL = "stable"

    /** Stable for new installs; keep an existing beta opt-in. */
    fun updateChannel(savedValue: String?): String =
        if (savedValue?.trim()?.equals("beta", ignoreCase = true) == true) "beta" else UPDATE_CHANNEL
}
