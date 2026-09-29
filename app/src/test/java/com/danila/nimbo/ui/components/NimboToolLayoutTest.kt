package com.danila.nimbo.ui.components

import org.junit.Assert.*
import org.junit.Test

class NimboToolLayoutTest {
    @Test fun narrowPhoneCommandsStack() {
        assertEquals(1, toolGridColumns(288f, 1f))
        assertEquals(1, toolGridColumns(326f, 1.25f))
        assertEquals(1, toolGridColumns(358f, 2f))
    }
    @Test fun largerSurfacesUseAvailableWidth() {
        assertEquals(2, toolGridColumns(358f, 1f))
        assertEquals(3, toolGridColumns(480f, 1f))
        assertEquals(4, toolGridColumns(1200f, 1f))
    }
    @Test fun scaleNeverCreatesMoreColumns() {
        listOf(256f, 288f, 358f, 480f, 808f).forEach { width ->
            var previous = 4
            listOf(1f, 1.25f, 1.5f, 2f, 3f).forEach { scale ->
                val current = toolGridColumns(width, scale)
                assertTrue(current in 1..previous)
                previous = current
            }
        }
    }
    @Test fun tinyWidthsAndReducedScaleStayUsable() {
        assertEquals(1, toolGridColumns(0f, 2f))
        assertEquals(toolGridColumns(358f, 1f), toolGridColumns(358f, 0.8f))
    }
    @Test fun compactMetricsUseTheirOwnMinimum() {
        assertEquals(2, toolGridColumns(256f, 1f, 112f))
        assertEquals(1, toolGridColumns(256f, 1.5f, 112f))
    }
}
