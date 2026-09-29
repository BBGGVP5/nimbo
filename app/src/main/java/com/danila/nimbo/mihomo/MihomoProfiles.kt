package com.danila.nimbo.mihomo

import android.content.Context
import com.danila.nimbo.model.Server
import com.danila.nimbo.network.NativeMihomoDocument
import com.danila.nimbo.service.SubscriptionUpdateEvents
import com.danila.nimbo.subscription.SubscriptionParserMigration
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import com.danila.nimbo.vpn.VpnCoreChoice
import java.util.UUID
import java.security.MessageDigest

/** Native YAML is a document, NOT a list of rewritten share links. */
object MihomoProfiles {
    /** Auto chooses Mihomo for this profile; selecting it must not override Auto. */
    internal fun coreForSelection(current: String): String =
        if (VpnCoreChoice.fromId(current) == VpnCoreChoice.AUTO) VpnCoreChoice.AUTO.id else VpnCoreChoice.MIHOMO.id

    fun isMihomo(profile: SubscriptionProfile): Boolean = profile.configType == "mihomo"
    fun isMihomo(server: Server): Boolean = server.protocol.equals("mihomo", ignoreCase = true)

    fun list(context: Context): List<SubscriptionProfile> =
        PreferencesManager(context).loadProfiles().filter(::isMihomo)

    fun source(context: Context, server: Server): String {
        if (!isMihomo(server)) throw MihomoException("INCOMPATIBLE_PROFILE")
        val profile = list(context).singleOrNull { it.url == server.profileUrl }
            ?: throw MihomoException("PROFILE_NOT_FOUND")
        if (profile.servers.none { it.uuid == server.uuid && isMihomo(it) }) throw MihomoException("PROFILE_NOT_FOUND")
        return profile.rawConfig?.also(MihomoProtocol::checkSource) ?: throw MihomoException("PROFILE_SOURCE_MISSING")
    }

    fun project(name: String, yaml: String, previous: SubscriptionProfile? = null,
                id: String = UUID.randomUUID().toString(), sourceUrl: String? = null,
                documentId: String? = null): SubscriptionProfile {
        MihomoProtocol.checkSource(yaml)
        val title = name.trim().take(120).ifBlank { "Mihomo" }
        val identity = documentId ?: previous?.mihomoDocumentId ?: "default"
        val persistedSource = sourceUrl ?: previous?.mihomoSourceUrl
        val stableId = if (persistedSource != null) stableProfileId(persistedSource, identity) else id
        val url = previous?.url ?: "mihomo://$stableId"
        val marker = Server(name = title, host = "mihomo.invalid", port = 0,
            uuid = previous?.servers?.singleOrNull()?.uuid ?: stableId, protocol = "mihomo", profileUrl = url,
            serverDescription = "Mihomo · YAML")
        return (previous ?: SubscriptionProfile()).copy(url = url, name = title,
            servers = listOf(marker), rawConfig = yaml, configType = "mihomo", error = null,
            mihomoSourceUrl = persistedSource, mihomoDocumentId = identity,
            isLoading = false, parserRevision = SubscriptionParserMigration.CURRENT_REVISION)
    }

    fun importProfile(context: Context, name: String, yaml: String, replaceUrl: String? = null,
                      sourceUrl: String? = null, documentId: String = "default"): SubscriptionProfile {
        MihomoProtocol.checkSource(yaml)
        MihomoBridge.inspect(yaml)
        // Inspection is intentionally independent of Android VPN admission: the exact
        // document and declared groups remain browsable while mobile support expands.
        // MihomoManager.prepare repeats preflight before touching a running tunnel.
        var imported: SubscriptionProfile? = null
        val prefs = PreferencesManager(context)
        prefs.updateProfiles { existing ->
            replaceUrl?.let(::requireInactive)
            val previous = replaceUrl?.let { url -> existing.singleOrNull { it.url == url && isMihomo(it) }
                ?: throw MihomoException("PROFILE_NOT_FOUND") }
            val profile = project(name, yaml, previous, sourceUrl = sourceUrl, documentId = documentId)
            imported = profile
            if (previous == null) existing + profile else existing.map { if (it.url == previous.url) profile else it }
        }
        SubscriptionUpdateEvents.notifyProfilesChanged()
        return checkNotNull(imported)
    }

    /** Atomically replaces every Mihomo document fetched from one subscription URL. */
    fun importDocuments(
        context: Context,
        sourceUrl: String?,
        documents: List<NativeMihomoDocument>,
        expectedSourceHashes: Map<String, String>? = null,
        decorate: (SubscriptionProfile, SubscriptionProfile?) -> SubscriptionProfile = { profile, _ -> profile }
    ): List<SubscriptionProfile> {
        if (sourceUrl?.isBlank() == true || documents.isEmpty() || documents.size > 64 ||
            documents.map { it.documentId }.distinct().size != documents.size) {
            throw MihomoException("INVALID_PROFILE")
        }
        documents.forEach { document ->
            MihomoProtocol.checkSource(document.source)
            if (MihomoProtocol.sourceHash(document.source) != document.sourceHash ||
                MihomoBridge.inspect(document.source)["documentKind"]?.asString != "mihomo") {
                throw MihomoException("SOURCE_MISMATCH")
            }
        }
        var imported: List<SubscriptionProfile> = emptyList()
        val preferences = PreferencesManager(context)
        preferences.updateProfiles { existing ->
            val replacing = if (sourceUrl == null) emptyList() else existing.filter { profile ->
                (isMihomo(profile) && profile.mihomoSourceUrl == sourceUrl) ||
                    (!isMihomo(profile) && profile.url == sourceUrl)
            }
            replacing.forEach { requireInactive(it.url) }
            expectedSourceHashes?.let { expected ->
                val current = replacing.filter(::isMihomo).associate {
                    (it.mihomoDocumentId ?: "default") to MihomoProtocol.sourceHash(it.rawConfig.orEmpty())
                }
                if (current != expected) throw MihomoException("STALE_PROFILE")
            }
            val byIdentity = replacing.associateBy { it.mihomoDocumentId ?: "default" }
            val localBatchId = UUID.randomUUID().toString()
            val next = documents.map { document ->
                val previous = byIdentity[document.documentId]
                    ?: byIdentity["default"]?.takeIf { document.documentId == "default" }
                val base = project(document.name, document.source, previous,
                    id = if (sourceUrl != null) stableProfileId(sourceUrl, document.documentId)
                        else stableProfileId(localBatchId, document.documentId),
                    sourceUrl = sourceUrl, documentId = document.documentId)
                decorate(base, previous).also { candidate ->
                    if (!isMihomo(candidate) || candidate.rawConfig != document.source ||
                        candidate.mihomoSourceUrl != sourceUrl || candidate.mihomoDocumentId != document.documentId) {
                        throw MihomoException("INVALID_PROFILE")
                    }
                }
            }
            val firstIndex = replacing.minOfOrNull(existing::indexOf) ?: existing.size
            val remaining = existing.filterNot(replacing::contains).toMutableList()
            val insertion = firstIndex.coerceAtMost(remaining.size)
            remaining.addAll(insertion, next)
            imported = next
            remaining
        }
        SubscriptionUpdateEvents.notifyProfilesChanged()
        return imported
    }

    fun remove(context: Context, profileUrl: String) {
        requireInactive(profileUrl)
        val prefs = PreferencesManager(context)
        prefs.updateProfiles { profiles ->
            requireInactive(profileUrl)
            if (profiles.none { it.url == profileUrl && isMihomo(it) }) throw MihomoException("PROFILE_NOT_FOUND")
            profiles.filterNot { it.url == profileUrl }
        }
        if (VpnManager.selectedServer?.profileUrl == profileUrl) {
            VpnManager.selectedServer = null
            prefs.clearLastSelectedServer()
        }
        SubscriptionUpdateEvents.notifyProfilesChanged()
    }

    /** Called on the main thread; choosing a profile never starts a VPN. */
    fun select(context: Context, profile: SubscriptionProfile) {
        val current = list(context).singleOrNull { it.url == profile.url } ?: throw MihomoException("PROFILE_NOT_FOUND")
        val server = current.servers.singleOrNull()?.takeIf(::isMihomo) ?: throw MihomoException("INVALID_PROFILE")
        val preferences = PreferencesManager(context)
        preferences.vpnCore = coreForSelection(preferences.vpnCore)
        VpnManager.selectManualServer(context, server)
    }

    private fun requireInactive(url: String) {
        if (VpnManager.state.value != VpnState.DISCONNECTED &&
            (VpnManager.connectedServer.value?.profileUrl == url || VpnManager.selectedServer?.profileUrl == url)) {
            throw MihomoException("PROFILE_ACTIVE")
        }
    }

    private fun stableProfileId(sourceUrl: String, documentId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$sourceUrl\u0000$documentId".toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it) }
}
