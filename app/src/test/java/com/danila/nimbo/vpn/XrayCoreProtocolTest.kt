package com.danila.nimbo.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayCoreProtocolTest {

    @Test
    fun `run request follows libXray invoke API v3`() {
        val request = JSONObject(XrayCoreProtocol.runXrayFromJson("{\"outbounds\":[]}"))

        assertEquals(3, request.getInt("apiVersion"))
        assertEquals("runXray", request.getString("method"))
        assertEquals("{\"outbounds\":[]}", request.getJSONObject("payload").getString("xrayJson"))
        assertFalse(request.getJSONObject("payload").has("configJSON"))
    }

    @Test
    fun `stop and conversion also use current API envelope`() {
        val stop = JSONObject(XrayCoreProtocol.stopXray())
        assertEquals(3, stop.getInt("apiVersion"))
        assertEquals("stopXray", stop.getString("method"))
        val text = "vless://example#Имя\n\"quoted\""
        val convert = JSONObject(XrayCoreProtocol.convertShareLinksToXrayJson(text))
        assertEquals(3, convert.getInt("apiVersion"))
        assertEquals("convertShareLinksToXrayJson", convert.getString("method"))
        assertEquals(text, convert.getJSONObject("payload").getString("text"))
    }

    @Test
    fun `runtime environment preserves config and passes Android values as strings`() {
        val configured = JSONObject(
            XrayCoreProtocol.withAndroidRuntimeEnv(
                configJson = "{\"inbounds\":[],\"env\":{\"xray.buf.readv\":\"true\"}}",
                assetDirectory = "/data/user/0/com.danila.nimbo/files/xray-data",
                tunFd = 42
            )
        )

        val environment = configured.getJSONObject("env")
        assertEquals("true", environment.getString("xray.buf.readv"))
        assertEquals("/data/user/0/com.danila.nimbo/files/xray-data", environment.getString("xray.location.asset"))
        assertEquals("42", environment.getString("xray.tun.fd"))
        assertTrue(configured.has("inbounds"))
        assertFalse(configured.has("payload"))
    }
}
