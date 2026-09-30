package com.danila.nimbo.utils

import org.junit.Assert.*
import org.junit.Test

class SupportDiagnosticRedactorTest {
    @Test fun configHidesSecretsAddressesNamesAndUnknownKeys() {
        val raw = """{"proxies":[{"name":"Private node","type":"vless","server":"198.51.100.9","uuid":"secret-id","reality-opts":{"public-key":"secret-key","short-id":"abc123"},"headers":{"secret-user":"https://private.example/token"}}],"proxy-groups":[{"type":"url-test","interval":600,"tolerance":100,"lazy":true}],"https://sensitive.example/key":"secret"}"""
        val result = SupportDiagnosticRedactor.config(raw)
        for (secret in listOf("Private node", "198.51.100.9", "secret-id", "secret-key", "abc123", "secret-user", "https://", "sensitive.example")) assertFalse(secret, result.contains(secret))
        assertTrue(result.contains("vless")); assertTrue(result.contains("url-test")); assertTrue(result.contains("600")); assertTrue(result.contains("true"))
    }
    @Test fun malformedInputIsNeverReturnedVerbatim() {
        assertEquals("{\"available\":false}", SupportDiagnosticRedactor.config("password: private-secret"))
    }
    @Test fun headerValuesAndCustomHeaderNamesAreHidden() {
        val result = SupportDiagnosticRedactor.headers(mapOf("Set-Cookie" to listOf("session=secret"), "Profile-Title" to listOf("base64:private"), "User-private-secret" to listOf("value")))
        assertFalse(result.contains("secret")); assertFalse(result.contains("base64")); assertFalse(result.contains("User-private")); assertTrue(result.contains("set-cookie"))
    }
}
