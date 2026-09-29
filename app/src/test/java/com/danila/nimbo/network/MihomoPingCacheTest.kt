package com.danila.nimbo.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MihomoPingCacheTest {
    @Test fun keepsSuccessfulMeasurementsUntilTheNextCheck() {
        assertTrue(MihomoPingCache.valid(235))
        assertTrue(MihomoPingCache.valid(0))
        assertTrue(MihomoPingCache.valid(60_000))
        assertFalse(MihomoPingCache.valid(-1))
        assertFalse(MihomoPingCache.valid(60_001))
    }

    @Test fun distinguishesChangedOutboundWithoutStoringItsCredentials() {
        val original = """{"name":"node","server":"example.org","password":"secret-1"}"""
        val changed = """{"name":"node","server":"example.org","password":"secret-2"}"""
        val fingerprint = MihomoPingCache.fingerprint(original)
        assertEquals(fingerprint, MihomoPingCache.fingerprint(original))
        assertEquals(fingerprint, MihomoPingCache.fingerprint("""{"password":"secret-1","server":"example.org","name":"node"}"""))
        assertNotEquals(fingerprint, MihomoPingCache.fingerprint(changed))
        assertFalse(fingerprint.contains("secret"))
    }
}
