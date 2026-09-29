package com.danila.nimbo.mihomo

import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoProfileCoreSelectionTest {
    @Test fun selectingMihomoProfileDoesNotOverrideAutoCore() {
        assertEquals("auto", MihomoProfiles.coreForSelection("auto"))
        assertEquals("auto", MihomoProfiles.coreForSelection(" AUTO "))
        assertEquals("mihomo", MihomoProfiles.coreForSelection("mihomo"))
        assertEquals("mihomo", MihomoProfiles.coreForSelection("xray"))
    }
}
