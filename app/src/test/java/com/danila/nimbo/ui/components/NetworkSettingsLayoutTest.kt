package com.danila.nimbo.ui.components

import org.junit.Assert.*
import org.junit.Test

class NetworkSettingsLayoutTest {
    @Test fun narrowAndLargeTextUseStackedControls() {
        listOf(256f, 288f).forEach { width ->
            listOf(1f, 1.25f, 1.5f, 2f).forEach { scale ->
                assertTrue(networkRowStacks(width, scale))
            }
        }
        assertTrue(networkRowStacks(326f, 1.25f))
        assertTrue(networkChoicesStack(256f, 1.25f, 3))
        assertTrue(networkChoicesStack(256f, 2f, 2))
    }

    @Test fun normalPhoneAndWideLayoutsAvoidUnnecessaryTallRows() {
        assertFalse(networkRowStacks(326f, 1f))
        assertFalse(networkRowStacks(480f, 1.25f))
        assertFalse(networkChoicesStack(326f, 1f, 3))
        assertFalse(networkChoicesStack(480f, 1.25f, 3))
    }

    @Test fun noChoicesAndReducedFontScaleAreSafe() {
        assertFalse(networkChoicesStack(0f, 1.25f, 0))
        assertEquals(networkRowStacks(288f, 1f), networkRowStacks(288f, 0.8f))
        assertEquals(networkChoicesStack(288f, 1f, 3), networkChoicesStack(288f, 0.8f, 3))
    }
}

