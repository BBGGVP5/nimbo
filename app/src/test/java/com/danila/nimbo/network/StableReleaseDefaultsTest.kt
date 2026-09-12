package com.danila.nimbo.network

import com.danila.nimbo.BuildConfig
import com.danila.nimbo.model.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableReleaseDefaultsTest {
    @Test fun productionVersionUpgradesBetaFive() {
        assertEquals("1.2.0", BuildConfig.VERSION_NAME)
        assertTrue(BuildConfig.VERSION_CODE > 16)
        assertTrue(UpdatePolicy.isSemanticVersionNewer(BuildConfig.VERSION_NAME, "1.2.0-beta.5"))
    }

    @Test fun freshOrInvalidPreferenceUsesStableChannel() {
        for (value in listOf(null, "", "unknown", "stable", "STABLE")) {
            assertEquals(UpdateChannel.STABLE, UpdateChannel.fromPreference(value))
        }
    }

    @Test fun explicitBetaOptInIsPreserved() {
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromPreference("beta"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromPreference("BETA"))
    }
}
