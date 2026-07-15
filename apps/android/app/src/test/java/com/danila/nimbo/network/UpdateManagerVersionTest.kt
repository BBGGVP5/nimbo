package com.danila.nimbo.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerVersionTest {

    @Test
    fun sameVersionWithTagPrefixIsNotAnUpdate() {
        assertFalse(UpdateManager.isSemanticVersionNewer("v1.0.0", "1.0.0"))
    }

    @Test
    fun greaterSemanticVersionIsAnUpdate() {
        assertTrue(UpdateManager.isSemanticVersionNewer("v1.0.1", "1.0.0"))
        assertTrue(UpdateManager.isSemanticVersionNewer("v1.1.0", "1.0.9"))
    }

    @Test
    fun olderOrInvalidTagIsNotAnUpdate() {
        assertFalse(UpdateManager.isSemanticVersionNewer("v0.9.9", "1.0.0"))
        assertFalse(UpdateManager.isSemanticVersionNewer("latest", "1.0.0"))
    }
}
