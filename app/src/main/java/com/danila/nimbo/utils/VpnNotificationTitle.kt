package com.danila.nimbo.utils

import com.danila.nimbo.ui.screens.SubscriptionProfile

private val genericMihomoTitle = Regex("(?i)^mihomo(?:\\s+\\d+)?$")

/** A synthetic Mihomo marker is not the subscription name shown to the user. */
internal fun vpnNotificationProfileName(
    profiles: List<SubscriptionProfile>,
    profile: SubscriptionProfile?
): String? {
    profile ?: return null
    if (profile.configType != "mihomo") return profile.displayName.trim().ifBlank { null }
    profile.customName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val parentUrl = profile.mihomoParentUrl ?: profile.mihomoSourceUrl
    val parentName = profiles.firstOrNull { it.url == parentUrl && it.url != profile.url }
        ?.displayName?.trim()?.takeIf { it.isNotBlank() }
    val nativeName = profile.displayName.trim()
    return when {
        genericMihomoTitle.matches(profile.name.trim()) -> parentName ?: "Mihomo"
        nativeName.endsWith(" · Mihomo") -> parentName ?: nativeName.removeSuffix(" · Mihomo")
        nativeName.isNotBlank() -> nativeName
        else -> parentName ?: "Mihomo"
    }
}

internal fun vpnNotificationTitle(serverTitle: String, profileTitle: String?, mihomo: Boolean): String {
    if (!mihomo) return serverTitle.ifBlank { "Nimbo" }
    profileTitle?.trim()?.takeIf { it.isNotBlank() && !genericMihomoTitle.matches(it) }?.let { return it }
    return serverTitle.trim().takeUnless { it.isBlank() || genericMihomoTitle.matches(it) } ?: "Mihomo"
}
