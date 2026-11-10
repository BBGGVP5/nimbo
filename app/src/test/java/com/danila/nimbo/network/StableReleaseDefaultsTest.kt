package com.danila.nimbo.network

import com.danila.nimbo.BuildConfig
import com.danila.nimbo.model.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableReleaseDefaultsTest {
    @Test fun stableReleaseUpgradesBetaAndPreviousStable() {
        assertEquals("1.3.0", BuildConfig.VERSION_NAME)
        assertEquals(20, BuildConfig.VERSION_CODE)
        assertTrue(UpdatePolicy.isSemanticVersionNewer(BuildConfig.VERSION_NAME, "1.3.0-beta.2"))
        assertTrue(UpdatePolicy.isSemanticVersionNewer(BuildConfig.VERSION_NAME, "1.2.0"))
        assertTrue(UpdatePolicy.isSemanticVersionNewer(BuildConfig.VERSION_NAME, "1.2.0-beta.5"))
    }

    @Test fun freshOrInvalidPreferenceFollowsBuildChannel() {
        for (value in listOf(null, "", "unknown")) {
            // A stable build defaults fresh installs to the stable channel.
            assertEquals(UpdateChannel.STABLE, UpdateChannel.fromPreference(value))
            assertEquals(UpdateChannel.BETA, UpdateChannel.fromPreference(value, "1.3.0-beta.2"))
            assertEquals(UpdateChannel.STABLE, UpdateChannel.fromPreference(value, "1.3.0"))
            assertEquals(UpdateChannel.STABLE, UpdateChannel.fromPreference(value, "1.3.0+build-local"))
        }
    }

    @Test fun explicitBetaOptInIsPreserved() {
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromPreference("beta"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromPreference("BETA"))
    }

    @Test fun explicitStableOptInIsPreservedOnBeta() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromPreference("stable"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromPreference("STABLE"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromPreference("beta", "1.3.0"))
    }
}
