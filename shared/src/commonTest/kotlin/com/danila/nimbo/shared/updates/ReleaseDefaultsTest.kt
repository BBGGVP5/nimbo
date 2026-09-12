package com.danila.nimbo.shared.updates

import com.danila.nimbo.shared.ui.NimboUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseDefaultsTest {
    @Test fun freshInstallUsesStableRelease() {
        assertEquals("1.2.0", NimboUiState().appVersion)
        assertEquals("stable", NimboUiState().updateChannel)
        assertEquals("stable", ReleaseDefaults.updateChannel(null))
    }

    @Test fun explicitBetaOptInSurvivesStableUpgrade() {
        assertEquals("beta", ReleaseDefaults.updateChannel("beta"))
        assertEquals("beta", ReleaseDefaults.updateChannel("BETA"))
        assertEquals("stable", ReleaseDefaults.updateChannel("stable"))
    }

    @Test fun invalidSavedChannelFallsBackToStable() {
        assertEquals("stable", ReleaseDefaults.updateChannel(""))
        assertEquals("stable", ReleaseDefaults.updateChannel("unknown"))
    }
}
