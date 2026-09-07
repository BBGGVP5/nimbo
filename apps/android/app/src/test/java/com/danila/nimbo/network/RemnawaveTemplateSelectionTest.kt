package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

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

    @Test
    fun extractSelectableXrayRoutes_exposesEveryBalancerStrategy() {
        val config = JSONObject(
            """{
              "outbounds":[{"tag":"direct","protocol":"freedom"}],
              "routing":{"balancers":[
                {"tag":"balance-last-ping","strategy":{"type":"leastPing"}},
                {"tag":"balance-least-loaded","strategy":{"type":"leastLoad"}}
              ]}
            }"""
        )

        val routes = RemnawaveApiClient.extractSelectableXrayRoutes(config)

        assertEquals(2, routes.size)
        assertEquals("leastPing", routes[0].label)
        assertEquals("balance-least-loaded", routes[1].tag)
        assertTrue(routes.all { it.isBalancer })
    }

    @Test
    fun routedLoopbackConfig_isRecognizedAndExposed() {
        val config = JSONObject(
            """{
              "remarks":"Маршрут через NL",
              "outbounds":[{"tag":"nl-route","protocol":"loopback"}],
              "routing":{"rules":[{"type":"field","outboundTag":"nl-route"}]}
            }"""
        )

        assertTrue(RemnawaveApiClient.isUsableXrayClientConfig(config))
        val route = RemnawaveApiClient.extractSelectableXrayRoutes(config).single()
        assertEquals("nl-route", route.tag)
        assertEquals("Маршрут через NL", route.label)
        assertEquals(false, route.isBalancer)
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
