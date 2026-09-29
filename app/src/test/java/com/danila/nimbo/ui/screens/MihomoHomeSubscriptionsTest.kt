package com.danila.nimbo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoHomeSubscriptionsTest {
    private val parent = SubscriptionProfile(url = "https://example.test/sub", name = "Provider")
    private val native = SubscriptionProfile(url = "mihomo://one", name = "Provider · Mihomo",
        configType = "mihomo", mihomoParentUrl = parent.url)

    @Test fun normalSelectionKeepsOrdinaryCards() {
        assertEquals(listOf(parent), homeSubscriptionProfiles(listOf(parent), parent))
    }

    @Test fun nativeSelectionReplacesParentInsteadOfDuplicatingIt() {
        assertEquals(listOf(native), homeSubscriptionProfiles(listOf(parent), native))
    }

    @Test fun localYamlGetsItsOwnCard() {
        val local = native.copy(mihomoParentUrl = null)
        assertEquals(listOf(local, parent), homeSubscriptionProfiles(listOf(parent), local))
    }
}
