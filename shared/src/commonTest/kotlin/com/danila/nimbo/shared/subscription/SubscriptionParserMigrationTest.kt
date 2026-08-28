package com.danila.nimbo.shared.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionParserMigrationTest {
    @Test
    fun reparsesOnlyOutdatedUniqueProfiles() {
        val urls = SubscriptionParserMigration.pendingUrls(
            listOf(
                " https://example.test/a " to 0,
                "https://example.test/a" to 0,
                "https://example.test/current" to SubscriptionParserMigration.currentRevision,
                "" to 0
            )
        )

        assertEquals(listOf("https://example.test/a"), urls)
        assertTrue(SubscriptionParserMigration.needsMigration(0))
        assertFalse(SubscriptionParserMigration.needsMigration(SubscriptionParserMigration.currentRevision))
    }
}
