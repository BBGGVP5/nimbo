package com.danila.nimbo.sync

import com.danila.nimbo.ui.screens.SubscriptionProfile
import java.net.URI

/** Cross-sync v1 carries source links, not device-local native document IDs. */
internal fun crossSyncProfileLinks(profiles: List<SubscriptionProfile>): List<SyncSubscription> {
    fun internalUrl(url: String) = url.trim().substringBefore(':').equals("mihomo", ignoreCase = true)
    fun native(profile: SubscriptionProfile) = profile.configType == "mihomo" || internalUrl(profile.url)
    fun remote(url: String?): String? = url?.trim()?.takeIf { candidate ->
        runCatching { URI(candidate).let { it.host != null && (it.scheme.equals("https", true) || it.scheme.equals("http", true)) } }.getOrDefault(false)
    }
    val parents = profiles.filterNot(::native).associateBy { CrossSyncProtocol.canonicalSubscriptionUrl(it.url) }
    return profiles.mapNotNull { profile ->
        val url = if (native(profile)) remote(profile.mihomoParentUrl) ?: remote(profile.mihomoSourceUrl) ?: remote(profile.url)
            else profile.url.trim().takeIf(String::isNotBlank)
        if (url == null || internalUrl(url)) return@mapNotNull null
        val owner = parents[CrossSyncProtocol.canonicalSubscriptionUrl(url)] ?: profile
        SyncSubscription(if (native(owner)) url else owner.url.trim(), owner.customName ?: owner.name.takeIf(String::isNotBlank))
    }.distinctBy { CrossSyncProtocol.canonicalSubscriptionUrl(it.url) }
        .mapIndexed { index, subscription -> subscription.copy(order = index) }
}
