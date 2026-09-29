package com.danila.nimbo.subscription

import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserMigrationTest {
    @Test
    fun `legacy json without parser revision stays eligible for migration`() {
        val profile = Gson().fromJson(
            """{"url":"https://legacy.example/sub","name":"Legacy","servers":[]}""",
            SubscriptionProfile::class.java
        )

        assertEquals(0, profile.parserRevision)
        assertTrue(SubscriptionParserMigration.needsMigration(profile.parserRevision))
    }

    @Test
    fun `old profile is pending and current profile is not`() {
        assertTrue(SubscriptionParserMigration.needsMigration(0))
        assertFalse(
            SubscriptionParserMigration.needsMigration(
                SubscriptionParserMigration.CURRENT_REVISION
            )
        )
    }

    @Test
    fun `only outdated non-empty unique urls are selected`() {
        val current = SubscriptionParserMigration.CURRENT_REVISION

        assertEquals(
            listOf("https://one.example/sub", "https://three.example/sub"),
            SubscriptionParserMigration.pendingUrls(
                listOf(
                    "https://one.example/sub" to 0,
                    "https://two.example/sub" to current,
                    "" to 0,
                    "https://one.example/sub" to 0,
                    "https://three.example/sub" to (current - 1)
                )
            )
        )
    }

    @Test
    fun `future revision is never downgraded`() {
        assertFalse(
            SubscriptionParserMigration.needsMigration(
                SubscriptionParserMigration.CURRENT_REVISION + 1
            )
        )
    }
}
