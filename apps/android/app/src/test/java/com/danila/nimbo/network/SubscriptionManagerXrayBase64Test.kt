package com.danila.nimbo.network

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionManagerXrayBase64Test {
    @Test
    fun `XRAY_BASE64 payload decodes into a usable Xray client config`() {
        val config = """{"outbounds":[{"tag":"proxy","protocol":"vless"}]}"""
        val payload = Base64.getEncoder().encodeToString(config.toByteArray())

        val decoded = SubscriptionManager.decodeSubscriptionBase64(payload)

        assertEquals(config, decoded)
        assertTrue(RemnawaveApiClient.isUsableXrayClientConfig(org.json.JSONObject(decoded!!)))
    }
}
