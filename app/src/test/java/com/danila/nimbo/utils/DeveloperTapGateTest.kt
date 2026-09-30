package com.danila.nimbo.utils

import org.junit.Assert.*
import org.junit.Test

class DeveloperTapGateTest {
    @Test fun exactlyFiveFastTapsUnlock() {
        val gate = DeveloperTapGate()
        for (i in 0..3) assertFalse(gate.tap(i * 200L))
        assertTrue(gate.tap(800))
        assertFalse(gate.tap(900))
    }
    @Test fun SlowTapsDoNotUnlock() {
        val gate = DeveloperTapGate()
        for (i in 0..8) assertFalse(gate.tap(i * 2100L))
    }
}
