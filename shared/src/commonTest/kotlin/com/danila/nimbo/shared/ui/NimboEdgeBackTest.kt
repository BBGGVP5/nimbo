package com.danila.nimbo.shared.ui

import kotlin.test.*

class NimboEdgeBackTest {
    @Test fun onlyLongHorizontalBackSwipeCommits() {
        assertTrue(nimboBackSwipeCommits(90f, 10f, 80f))
        assertFalse(nimboBackSwipeCommits(20f, 0f, 80f))
        assertFalse(nimboBackSwipeCommits(-90f, 0f, 80f))
        assertFalse(nimboBackSwipeCommits(90f, 100f, 80f))
    }
    @Test fun newestBackActionWinsAndDisposedPagesDoNotIntercept() {
        val actions = NimboBackActions()
        val parent = Any(); val editor = Any()
        var result = ""
        actions.add(parent) { result = "parent" }
        actions.add(editor) { result = "confirm-unsaved" }
        actions.back { result = "home" }
        assertEquals("confirm-unsaved", result)
        actions.remove(editor)
        actions.back { result = "home" }
        assertEquals("parent", result)
        actions.remove(parent)
        actions.back { result = "home" }
        assertEquals("home", result)
    }
}
