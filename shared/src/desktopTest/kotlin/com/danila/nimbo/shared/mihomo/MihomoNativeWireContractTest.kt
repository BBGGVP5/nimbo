package com.danila.nimbo.shared.mihomo

import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/** Actual pinned Go output, independently hashed original UTF-8 bytes. */
class MihomoNativeWireContractTest {
    @Test fun actualNativeGoldenRoundTripsWithoutLosingGroupMetadata() {
        val fixtures = listOf(File("../tools/native/mihomo-core/testdata"), File("tools/native/mihomo-core/testdata"))
            .first { File(it, "inspect-wire-v1.json").isFile }
        val bytes = File(fixtures, "inspect-source.yaml").readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val result = assertIs<MihomoInspectionResult.Inspected>(MihomoNativeInspection.decode(
            File(fixtures, "inspect-wire-v1.json").readText(Charsets.UTF_8), "cli-inspect", bytes.toString(Charsets.UTF_8), hash))
        assertEquals(bytes.toString(Charsets.UTF_8), result.document.originalText)
        val graph = result.document.graph!!
        assertEquals("PublicDirect", graph.proxies.single().name)
        assertEquals("Inline", graph.providers.single().name)
        assertEquals("inline", graph.providers.single().type)
        val group = graph.groups.single()
        assertEquals("PublicChoice", group.name)
        assertEquals(listOf("PublicDirect", "DIRECT", "REJECT"), group.proxies)
        assertEquals(listOf("Inline"), group.use)
        assertTrue(group.includesAllProviders)
        assertFalse(group.hidden)
        assertEquals("public-icon", group.icon)
        assertTrue(result.document.unsupportedFeatures.isEmpty())
    }
}
