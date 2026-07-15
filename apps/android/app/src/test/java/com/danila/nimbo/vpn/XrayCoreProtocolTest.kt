package com.danila.nimbo.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayCoreProtocolTest {

    @Test
    fun `run request follows libXray invoke API v1`() {
        val request = JSONObject(XrayCoreProtocol.runXrayFromJson("{\"outbounds\":[]}"))

        assertEquals(1, request.getInt("apiVersion"))
        assertEquals("runXrayFromJson", request.getString("method"))
        assertEquals("{\"outbounds\":[]}", request.getJSONObject("payload").getString("configJSON"))
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
