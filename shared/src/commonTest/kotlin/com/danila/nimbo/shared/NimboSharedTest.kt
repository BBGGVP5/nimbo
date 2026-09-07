package com.danila.nimbo.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class NimboSharedTest {
    @Test
    fun exposesStableRuntimeLabel() {
        assertEquals("Nimbo shared/2 (test)", NimboShared.runtimeLabel("test"))
    }
}
