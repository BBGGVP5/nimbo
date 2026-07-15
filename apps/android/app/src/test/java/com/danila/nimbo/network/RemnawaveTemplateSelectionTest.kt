package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemnawaveTemplateSelectionTest {

    @Test
    fun selectTemplateByHints_fallsBackToNameWhenUuidIsStale() {
        val templates = listOf(
            template(uuid = "uuid-1", name = "autobalancers"),
            template(uuid = "uuid-2", name = "autobalancers2")
        )

        val selected = RemnawaveApiClient.selectTemplateByHints(
            templates = templates,
            templateUuidHint = "old-uuid-from-cache",
            templateNameHint = "autobalancers2"
        )

        assertEquals("uuid-2", selected?.uuid)
    }

    @Test
    fun selectTemplateByHints_prefersUuidWhenItMatches() {
        val templates = listOf(
            template(uuid = "uuid-1", name = "autobalancers"),
            template(uuid = "uuid-2", name = "autobalancers2")
        )

        val selected = RemnawaveApiClient.selectTemplateByHints(
            templates = templates,
            templateUuidHint = "uuid-1",
            templateNameHint = "autobalancers2"
        )

        assertEquals("uuid-1", selected?.uuid)
    }

    @Test
    fun selectTemplateByHints_returnsNullWhenNothingMatches() {
        val templates = listOf(
            template(uuid = "uuid-1", name = "autobalancers")
        )

        val selected = RemnawaveApiClient.selectTemplateByHints(
            templates = templates,
            templateUuidHint = "missing",
            templateNameHint = "missing"
        )

        assertNull(selected)
    }

    @Test
    fun hasUsableXrayClientOutboundProtocol_rejectsServerSideDirectOnlyTemplate() {
        val protocols = listOf("freedom", "blackhole", "freedom")

        assertEquals(false, RemnawaveApiClient.hasUsableXrayClientOutboundProtocol(protocols))
    }

    @Test
    fun hasUsableXrayClientOutboundProtocol_acceptsClientProxyOutbound() {
        val protocols = listOf("vless", "freedom")

        assertEquals(true, RemnawaveApiClient.hasUsableXrayClientOutboundProtocol(protocols))
    }

    private fun template(uuid: String, name: String): RemnawaveSubscriptionTemplate {
        return RemnawaveSubscriptionTemplate(
            uuid = uuid,
            name = name,
            templateType = "XRAY_JSON",
            viewPosition = 0
        )
    }
}
