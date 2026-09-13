package com.danila.nimbo.vpn

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HealthProxyRouteTest {
    private fun config(): JSONObject = JSONObject().apply {
        put("inbounds", JSONArray().also(LocalProxyConfig::ensureInbound))
        put("outbounds", JSONArray()
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "node-a").put("protocol", "vless")))
        put("routing", JSONObject().put("rules", LocalProxyConfig.prependRoute(
            JSONArray().put(JSONObject().put("type", "field").put("outboundTag", "direct")), "node-a", null)))
    }
    private fun verify(config: JSONObject): Boolean = LocalProxyConfig.authenticateVerifiedRoute(config, HealthProxySession("a"))

    @Test fun `first dedicated rule overrides direct default and installs ephemeral auth`() {
        val config = config()
        val session = HealthProxySession("a")
        assertTrue(LocalProxyConfig.authenticateVerifiedRoute(config, session))
        val accounts = config.getJSONArray("inbounds").getJSONObject(0).getJSONObject("settings").getJSONArray("accounts")
        assertEquals(1, accounts.length())
        assertEquals(session.username, accounts.getJSONObject(0).getString("user"))
        assertEquals(session.password, accounts.getJSONObject(0).getString("pass"))
        assertNotEquals(session.password, HealthProxySession("a").password)
    }

    @Test fun `reject direct missing shadowed conditional and ambiguous routes`() {
        for (tag in listOf("direct", "missing")) {
            val config = config()
            config.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).put("outboundTag", tag)
            assertFalse(verify(config))
        }
        val conditional = config()
        conditional.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).put("domain", JSONArray().put("example.com"))
        assertFalse(verify(conditional))
        val shadowed = config()
        shadowed.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).remove("inboundTag")
        assertFalse(verify(shadowed))
        val ambiguous = config()
        ambiguous.getJSONArray("outbounds").put(JSONObject().put("tag", "node-b").put("protocol", "trojan"))
        assertFalse(verify(ambiguous))
        assertTrue(LocalProxyConfig.authenticateVerifiedRoute(ambiguous, HealthProxySession("a"), "node-a"))
        assertFalse(LocalProxyConfig.authenticateVerifiedRoute(ambiguous, HealthProxySession("b"), "node-b"))
    }

    @Test fun `reject chained routes and nonloopback listener`() {
        val chained = config()
        chained.getJSONArray("outbounds").getJSONObject(1).put("proxySettings", JSONObject().put("tag", "direct"))
        assertFalse(verify(chained))
        val dialer = config()
        dialer.getJSONArray("outbounds").getJSONObject(1).put("streamSettings", JSONObject()
            .put("sockopt", JSONObject().put("dialerProxy", "direct")))
        assertFalse(verify(dialer))
        val public = config()
        public.getJSONArray("inbounds").getJSONObject(0).put("listen", "0.0.0.0")
        assertFalse(verify(public))
    }

    @Test fun `balancer requires safe candidates and safe failure path`() {
        val config = config()
        val routing = config.getJSONObject("routing")
        routing.put("rules", LocalProxyConfig.prependRoute(JSONArray(), null, "auto"))
        val balancer = JSONObject().put("tag", "auto").put("selector", JSONArray().put("node-"))
        routing.put("balancers", JSONArray().put(balancer))
        // Empty selection/failed observations would use the direct default.
        assertFalse(verify(config))
        balancer.put("fallbackTag", "node-a")
        assertTrue(verify(config))
        balancer.put("fallbackTag", "direct")
        assertFalse(verify(config))
        balancer.put("fallbackTag", "node-a").put("selector", JSONArray().put("direct"))
        assertFalse(verify(config))
    }

    @Test fun `reject nested XHTTP download dialer and nested proxy settings`() {
        for (transport in listOf("xhttpSettings", "splithttpSettings")) {
            for (override in listOf(
                JSONObject().put("dialerProxy", "direct"),
                JSONObject().put("proxySettings", JSONObject().put("tag", "direct"))
            )) {
                val config = config()
                config.getJSONArray("outbounds").getJSONObject(1).put("streamSettings", JSONObject()
                    .put(transport, JSONObject().put("extra", JSONObject()
                        .put("downloadSettings", JSONObject().put("sockopt", override)))))
                assertFalse(verify(config))
            }
        }
    }

    @Test fun `routing words in plain header values and blank dialers are harmless`() {
        val config = config()
        config.getJSONArray("outbounds").getJSONObject(1).put("streamSettings", JSONObject()
            .put("xhttpSettings", JSONObject().put("headers", JSONObject()
                .put("X-Note", "dialerProxy and proxySettings are ordinary text here")))
            .put("sockopt", JSONObject().put("dialerProxy", "")))
        assertTrue(verify(config))
    }

    @Test fun `uncertified route keeps internal authenticated probes but cannot expose Nimbo ping`() {
        try {
            val session = HealthProxySession("a")
            HealthProxySessions.activate(session, verifiedRoute = false)
            assertSame(session, HealthProxySessions.active)
            assertFalse(session.routeVerified)
            assertNull(HealthProxySessions.connected())
        } finally { HealthProxySessions.invalidate() }
    }

    @Test fun `disconnect invalidates the session and same-node reconnect uses new identity`() {
        try {
            val old = HealthProxySession("a")
            HealthProxySessions.activate(old)
            assertSame(old, HealthProxySessions.active)
            HealthProxySessions.invalidate()
            assertNull(HealthProxySessions.active)
            HealthProxySessions.activate(HealthProxySession("a"))
            assertNotSame(old, HealthProxySessions.active)
        } finally { HealthProxySessions.invalidate() }
    }
}
