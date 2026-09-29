package com.danila.nimbo.mihomo

import com.danila.nimbo.network.NativeMihomoDocument
import com.danila.nimbo.ui.screens.SubscriptionProfile

/** The server-rendered documents and the actual endpoint used to refresh them. */
data class MihomoSubscriptionTemplates(val sourceUrl: String, val documents: List<NativeMihomoDocument>) {
    internal fun belongsTo(profile: SubscriptionProfile, parent: SubscriptionProfile): Boolean =
        MihomoProfiles.isMihomo(profile) &&
            (profile.mihomoParentUrl == parent.url || profile.mihomoSourceUrl == sourceUrl)

    /** Keep ordinary servers alongside the native configuration; never flatten its groups. */
    fun mergeInto(
        profiles: List<SubscriptionProfile>,
        parent: SubscriptionProfile,
        protectedUrls: Set<String> = emptySet()
    ): List<SubscriptionProfile> {
        if (documents.isEmpty()) return profiles
        require(documents.size <= 64 && documents.map { it.documentId }.distinct().size == documents.size)
        documents.forEach { require(MihomoProtocol.sourceHash(it.source) == it.sourceHash) }
        val previous = profiles.filter { belongsTo(it, parent) }
        if (previous.any { it.url in protectedUrls }) return profiles
        val byId = previous.associateBy { it.mihomoDocumentId ?: "default" }
        val next = documents.mapIndexed { index, document ->
            val old = byId[document.documentId]
            val title = if (document.name.matches(Regex("Mihomo \\d+")))
                "${parent.displayName} · Mihomo" + if (documents.size > 1) " ${index + 1}" else ""
                else document.name
            MihomoProfiles.project(title, document.source, old, sourceUrl = sourceUrl, documentId = document.documentId).copy(
                mihomoParentUrl = parent.url,
                uploadTotal = parent.uploadTotal, downloadTotal = parent.downloadTotal,
                totalTraffic = parent.totalTraffic, expireTime = parent.expireTime,
                deviceCount = parent.deviceCount, deviceLimit = parent.deviceLimit, onlineDevices = parent.onlineDevices,
                announce = parent.announce, websiteUrl = parent.websiteUrl, supportUrl = parent.supportUrl,
                brandLogo = parent.brandLogo, brandLogoCache = parent.brandLogoCache,
                autoUpdateInterval = parent.autoUpdateInterval, daysUntilExpiry = parent.daysUntilExpiry
            )
        }
        val index = previous.minOfOrNull(profiles::indexOf)
            ?: (profiles.indexOfFirst { it.url == parent.url }.takeIf { it >= 0 }?.plus(1) ?: profiles.size)
        return profiles.filterNot(previous::contains).toMutableList().apply { addAll(index.coerceAtMost(size), next) }
    }
}
