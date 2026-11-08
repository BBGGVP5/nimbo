package com.danila.nimbo.vpn

import com.danila.nimbo.mihomo.MihomoBridge
import com.danila.nimbo.mihomo.MihomoProtocol
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdBlockingRulesTest {
    private fun template() = JSONObject("""{
        "outbounds":[{"tag":"proxy","protocol":"vless"},{"tag":"provider-block","protocol":"blackhole"}],
        "routing":{"domainStrategy":"AsIs","rules":[
            {"type":"field","inboundTag":["tun-in"],"outboundTag":"proxy"},
            {"type":"field","domain":["domain:provider.example"],"outboundTag":"provider-block"},
            {"type":"field","network":"tcp,udp","outboundTag":"proxy"}
        ]}
    }""")

    @Test fun offPreservesTemplateAndProviderBlockingExactly() {
        val original = template()
        val before = original.toString()
        assertSame(original, AdBlockingRules.overlay(original, false))
        assertEquals(before, original.toString())
    }

    @Test fun onPrecedesCatchallsAndPreservesAllProviderRulesAndOriginalSource() {
        val original = template()
        val before = original.toString()
        val result = AdBlockingRules.overlay(original, true)
        assertEquals(before, original.toString())
        val rules = result.getJSONObject("routing").getJSONArray("rules")
        assertEquals(4, rules.length())
        assertEquals("provider-block", rules.getJSONObject(0).getString("outboundTag"))
        val domains = rules.getJSONObject(0).getJSONArray("domain")
        assertEquals(20, domains.length())
        assertTrue((0 until domains.length()).map(domains::getString).contains("domain:doubleclick.net"))
        for (i in 0..2) assertEquals(original.getJSONObject("routing").getJSONArray("rules").get(i).toString(),
            rules.get(i + 1).toString())
        assertEquals("AsIs", result.getJSONObject("routing").getString("domainStrategy"))
    }

    @Test fun generatedConfigUsesActualBlackholeEvenIfProviderHasConflictingTag() {
        val original = JSONObject("""{"outbounds":[{"tag":"nimbo-ad-block","protocol":"freedom"}],
            "routing":{"rules":[{"type":"field","outboundTag":"nimbo-ad-block"}]}}""")
        val result = AdBlockingRules.overlay(original, true)
        val tag = result.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("outboundTag")
        assertNotEquals("nimbo-ad-block", tag)
        val outbounds = result.getJSONArray("outbounds")
        assertEquals("blackhole", outbounds.getJSONObject(1).getString("protocol"))
        assertEquals(tag, outbounds.getJSONObject(1).getString("tag"))
        assertEquals(1, original.getJSONArray("outbounds").length())
    }

    @Test fun adBlockingForcesDomainSniffingWithoutTurningOnUserRouting() {
        assertTrue(RoutingRuntimePolicy.shouldEnableSniffing(false, false, true))
        assertFalse(RoutingRuntimePolicy.shouldEnableSniffing(false, false, false))
    }

    @Test fun finalRuntimeOverlayEnablesTunDestinationDetectionAndSurvivesRuntimeEnvelope() {
        val original = template().put("inbounds", org.json.JSONArray().put(JSONObject()
            .put("protocol", "tun").put("tag", "tun-in")
            .put("sniffing", JSONObject().put("enabled", false))))
        val effective = AdBlockingRules.overlay(original, true)
        val runtime = JSONObject(XrayCoreProtocol.withAndroidRuntimeEnv(effective.toString(), "/assets", 23))
        assertTrue(runtime.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getBoolean("enabled"))
        assertTrue(runtime.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getBoolean("routeOnly"))
        assertFalse(original.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getBoolean("enabled"))
        assertTrue(runtime.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
            .getJSONArray("domain").toString().contains("domain:doubleclick.net"))
        assertEquals("23", runtime.getJSONObject("env").getString("xray.tun.fd"))
    }

    @Test fun mihomoOptionIsRuntimeOnlyAndDefaultOffStaysCompatibleWithOldBridge() {
        val original = "mode: rule\nrules:\n  - DOMAIN-SUFFIX,provider.example,REJECT\n  - MATCH,DIRECT\n"
        val off = MihomoBridge.androidStartFields("/data", false, emptyList())
        assertFalse(off.getAsJsonObject("options").has("adBlocking"))
        val on = MihomoBridge.androidStartFields("/data", false, emptyList(), true)
        val request = MihomoProtocol.request("start", original, fields = on)
        assertTrue(request.getAsJsonObject("options")["adBlocking"].asBoolean)
        assertEquals(original, request["yaml"].asString)
    }
}
