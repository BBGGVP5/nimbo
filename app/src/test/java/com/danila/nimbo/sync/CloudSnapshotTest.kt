package com.danila.nimbo.sync

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class CloudSnapshotTest {
    @Test fun encryptedRoundTripHasNoSourceAndFreshNonce() {
        val password = "secret test password".toCharArray()
        val source = "subscription-private-token"
        val first = CloudCipher.encrypt(source, password)
        val second = CloudCipher.encrypt(source, password)
        assertFalse(first.contains(source)); assertNotEquals(first, second)
        assertEquals(source, CloudCipher.decrypt(first, password))
    }
    @Test fun wrongPasswordCannotImport() {
        val encrypted = CloudCipher.encrypt("{}", "valid password".toCharArray())
        assertThrows(Exception::class.java) { CloudCipher.decrypt(encrypted, "wrong password".toCharArray()) }
    }
    @Test fun noPlaintextOrShortPasswordFallback() {
        assertThrows(IllegalArgumentException::class.java) { CloudCipher.encrypt("{}", charArrayOf()) }
        assertThrows(Exception::class.java) { CloudCipher.decrypt("""{"format":"nimbo-cloud-encrypted-v1","salt":"","iv":"","data":""}""", "valid password".toCharArray()) }
    }
    @Test fun profilesRequireVersionedCompleteSchema() {
        assertThrows(Exception::class.java) { CloudProfiles.decode("""{"format":"unknown","profiles":[]}""") }
        assertThrows(Exception::class.java) { CloudProfiles.decode("""{"format":"nimbo-cloud-profiles-v1","profiles":[{"url":""}]}""") }
    }
}
