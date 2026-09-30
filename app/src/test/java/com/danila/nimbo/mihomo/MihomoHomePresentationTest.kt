package com.danila.nimbo.mihomo

import org.junit.Assert.*
import org.junit.Test

class MihomoHomePresentationTest {
    @Test fun automaticProfileDoesNotAskForAManualLocation() {
        val state = mihomoHomePresentation(null, null, null, false, false)
        assertEquals("Авто · правила профиля", state.title)
        assertEquals("Группы выберут сервер при подключении", state.subtitle)
    }
    @Test fun connectedProfileWithoutSnapshotDoesNotInventAServer() {
        val state = mihomoHomePresentation(null, null, null, true, true)
        assertEquals("Traffic follows the profile's groups", state.subtitle)
    }
    @Test fun savedManualChoiceKeepsItsGroup() {
        assertEquals(MihomoHomePresentation("FI", "Sites"),
            mihomoHomePresentation(null, "FI", "Sites", false, true))
    }
    @Test fun liveSelectionShowsActualResolvedMember() {
        val live = MihomoActiveSelection("Auto", "LV", false)
        assertEquals("Сейчас: LV", mihomoHomePresentation(live, "Auto", "Sites", true, false).subtitle)
    }
    @Test fun balancingDoesNotClaimOneFixedNode() {
        val live = MihomoActiveSelection("Pool", null, true)
        assertEquals("Server chosen per connection", mihomoHomePresentation(live, null, null, true, true).subtitle)
    }
}
