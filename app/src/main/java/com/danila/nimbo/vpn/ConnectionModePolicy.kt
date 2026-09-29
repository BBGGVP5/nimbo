package com.danila.nimbo.vpn

/** Saved selection is independent of whether a tunnel is currently desired. */
internal object ConnectionModePolicy {
    fun shouldApplyImmediately(active: Boolean, switchAllowed: Boolean): Boolean =
        active && switchAllowed

    fun recoveryEnabled(autoSelected: Boolean, autoReconnect: Boolean): Boolean =
        autoSelected || autoReconnect

    /** A missing/deleted Auto profile must not silently connect a manual fallback. */
    fun candidates(profileUrl: String?, profiles: Map<String, List<String>>): List<String> =
        profiles[profileUrl].orEmpty().distinct()
}
