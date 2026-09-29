package com.danila.nimbo.shared.updates

import com.danila.nimbo.shared.ui.NimboUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseDefaultsTest {
    @Test fun freshInstallUsesBetaRelease() {
        assertEquals("1.3.0-beta.1", NimboUiState().appVersion)
        assertEquals("beta", NimboUiState().updateChannel)
        assertEquals("beta", ReleaseDefaults.updateChannel(null))
    }

    @Test fun explicitChannelSurvivesBetaUpgrade() {
        assertEquals("beta", ReleaseDefaults.updateChannel("beta"))
        assertEquals("beta", ReleaseDefaults.updateChannel("BETA"))
        assertEquals("stable", ReleaseDefaults.updateChannel("stable"))
        assertEquals("stable", ReleaseDefaults.updateChannel(" STABLE "))
    }

    @Test fun invalidSavedChannelFallsBackToBeta() {
        assertEquals("beta", ReleaseDefaults.updateChannel(""))
        assertEquals("beta", ReleaseDefaults.updateChannel("unknown"))
    }
}
