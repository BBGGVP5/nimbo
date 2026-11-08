package com.danila.nimbo.sync

import com.danila.nimbo.ui.screens.SubscriptionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSyncProfileLinksTest {
    private val parent = SubscriptionProfile(url = "https://example.test/sub?token=ExactCase", name = "Provider", customName = "My provider")
    private val child = SubscriptionProfile(url = "mihomo://child", name = "Provider · Mihomo", configType = "mihomo", mihomoParentUrl = parent.url, mihomoSourceUrl = "https://example.test/sub/mihomo", rawConfig = "exact YAML")

    @Test fun companionTransfersParentOnceRegardlessOfLocalOrder() {
        val expected = listOf(SyncSubscription(parent.url, "My provider", 0))
        assertEquals(expected, crossSyncProfileLinks(listOf(parent, child)))
        assertEquals(expected, crossSyncProfileLinks(listOf(child, parent)))
    }

    @Test fun remotelyImportedNativeProfileTransfersActualSourceLink() {
        val standalone = child.copy(mihomoParentUrl = null)
        assertEquals(listOf(SyncSubscription(standalone.mihomoSourceUrl!!, standalone.name, 0)), crossSyncProfileLinks(listOf(standalone)))
        assertEquals("exact YAML", standalone.rawConfig)
    }

    @Test fun localYamlCannotBeRepresentedByLinkOnlySync() {
        assertTrue(crossSyncProfileLinks(listOf(child.copy(mihomoParentUrl = null, mihomoSourceUrl = null))).isEmpty())
        assertTrue(crossSyncProfileLinks(listOf(SubscriptionProfile(url = " MiHoMo://legacy ", name = "Old placeholder"))).isEmpty())
    }

    @Test fun providerNamesDoNotIdentifyInternalProfiles() {
        val normal = parent.copy(name = "Provider · Mihomo", customName = null)
        assertEquals(listOf(SyncSubscription(parent.url, normal.name, 0)), crossSyncProfileLinks(listOf(normal)))
    }

    @Test fun internalSourceLinksAreNeverExportedAsFallback() {
        assertTrue(crossSyncProfileLinks(listOf(child.copy(mihomoParentUrl = "mihomo://bad", mihomoSourceUrl = "file:///private.yaml"))).isEmpty())
    }

    @Test fun ordinaryInlineLinksAndContiguousOrderArePreserved() {
        val inline = SubscriptionProfile(url = "vless://user@host:443", name = "Inline")
        val links = crossSyncProfileLinks(listOf(child.copy(mihomoParentUrl = null, mihomoSourceUrl = null), parent, inline))
        assertEquals(listOf(SyncSubscription(parent.url, "My provider", 0), SyncSubscription(inline.url, "Inline", 1)), links)
    }
}
