package com.danila.nimbo.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceStatusProbeTest {
    private fun verdict(id: String, code: Int = 200, body: String = "", finalUrl: String = "https://example.com/") =
        ServiceStatusCatalog.byId(id)!!.classify(ProbeResponse(code, finalUrl, body, 100)).availability

    @Test
    fun parsesCloudflareTrace() {
        val info = ServiceStatusProbe.parseTrace("fl=1\nip=130.78.190.184\nloc=EE\ncolo=TLL\n")
        assertEquals(ExitInfo("130.78.190.184", "EE"), info)
        assertNull(ServiceStatusProbe.parseTrace("ip=1.2.3.4\nloc=XX")!!.countryCode)
        assertNull(ServiceStatusProbe.parseTrace("loc=EE"))
    }

    @Test
    fun geminiUsesPageFlag() {
        assertEquals(ServiceAvailability.REGION_BLOCKED, verdict("gemini", body = """x\"45631641\":false,\"1\" ,2,1,200,"RUS""""))
        assertEquals(ServiceAvailability.AVAILABLE, verdict("gemini", body = "a45631641,null,true"))
        assertEquals(ServiceAvailability.ERROR, verdict("gemini", body = "<html></html>"))
    }

    @Test
    fun bilibiliAndOpenAiRegionSignals() {
        assertEquals(ServiceAvailability.REGION_BLOCKED, verdict("bilibili", body = """{"code":-10403,"message":"x"}"""))
        assertEquals(ServiceAvailability.AVAILABLE, verdict("bilibili", body = """{"code":0}"""))
        assertEquals(ServiceAvailability.REGION_BLOCKED,
            verdict("chatgpt", code = 403, body = """{"error":{"code":"unsupported_country_region_territory"}}"""))
        assertEquals(ServiceAvailability.AVAILABLE, verdict("chatgpt", body = """{"cookie_consent_required": true}"""))
    }

    @Test
    fun redirectsAndServerErrors() {
        assertEquals(ServiceAvailability.REGION_BLOCKED,
            verdict("claude", finalUrl = "https://www.anthropic.com/app-unavailable-in-region"))
        assertEquals(ServiceAvailability.AVAILABLE, verdict("claude", finalUrl = "https://claude.ai/login"))
        assertEquals(ServiceAvailability.ERROR, verdict("discord", code = 503))
        assertEquals(ServiceAvailability.ERROR, verdict("google", code = 200))
    }
}
