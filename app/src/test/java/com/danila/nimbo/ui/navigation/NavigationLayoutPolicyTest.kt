package com.danila.nimbo.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLayoutPolicyTest {
    @Test fun ordinaryPhoneHasFourTabs() { assertEquals(4, navigationColumnCount(346f, 1f)) }
    @Test fun largeTextGetsTwoEqualRows() { assertEquals(2, navigationColumnCount(346f, 1.3f)) }
    @Test fun narrowPhoneDoesNotWrapOnlySettings() { assertEquals(2, navigationColumnCount(276f, 1f)) }
    @Test fun wideViewportStillFitsFourTabs() { assertEquals(4, navigationColumnCount(500f, 1.3f)) }
}
