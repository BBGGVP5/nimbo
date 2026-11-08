package com.danila.nimbo.shared.routing

import kotlinx.serialization.json.*
import kotlin.test.*

class NimboAdBlockingTest {
    @Test fun offPreservesProviderBlockingAndOriginalRules() {
        val original = Json.parseToJsonElement("""[{"domain":["domain:provider.example"],"outboundTag":"block"},{"network":"tcp,udp","outboundTag":"proxy"}]""").jsonArray
        assertSame(original, NimboAdBlocking.prependXrayRules(original, false))
        val effective = NimboAdBlocking.prependXrayRules(original, true, "safe-blackhole")
        assertEquals(original, JsonArray(effective.drop(1)))
        assertEquals("safe-blackhole", effective.first().jsonObject["outboundTag"]?.jsonPrimitive?.content)
        assertEquals(2, original.size)
        assertTrue("domain:doubleclick.net" in effective.first().jsonObject.getValue("domain").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun overlayIsBoundedAndDefaultOff() {
        assertNull(NimboAdBlocking.xrayRule(false))
        assertEquals(20, NimboAdBlocking.suffixDomains.size)
        assertEquals(20, NimboAdBlocking.suffixDomains.toSet().size)
        assertTrue(NimboAdBlocking.suffixDomains.all { it.isNotBlank() && !it.contains(':') })
    }
}
