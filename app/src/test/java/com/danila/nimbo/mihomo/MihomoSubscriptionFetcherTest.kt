package com.danila.nimbo.mihomo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class MihomoSubscriptionFetcherTest {
    @Test
    fun acceptsExactUtf8AndPreservesBytes() {
        val yaml = "# exact\r\nproxies: []\r\n"
        assertEquals(yaml, MihomoSubscriptionFetcher.decode(ByteArrayInputStream(yaml.toByteArray(StandardCharsets.UTF_8))))
    }

    @Test
    fun rejectsNonHttpsCredentialsFragmentAndMalformedUtf8() {
        listOf(
            "http://example.com/config",
            "https://user:pass@example.com/config",
            "https://example.com/config#token"
        ).forEach { url ->
            assertThrows(MihomoException::class.java) { MihomoSubscriptionFetcher.checkedUrl(url) }
        }
        assertThrows(MihomoException::class.java) {
            MihomoSubscriptionFetcher.decode(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }

    @Test
    fun rejectsOversizedCandidateBeforePersistence() {
        val oversized = ByteArray(MihomoProtocol.MAX_SOURCE_BYTES + 1) { 'x'.code.toByte() }
        assertThrows(MihomoException::class.java) {
            MihomoSubscriptionFetcher.decode(ByteArrayInputStream(oversized))
        }
    }
}
