package com.danila.nimbo.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NimboPingPresentationTest {
    @Test fun legacyProtocolKeepsItsHttpMethod() {
        assertEquals("http_head", normalizePingProtocol("http"))
        for (key in listOf("nimbo", "tcp", "http_get", "http_head", "icmp")) {
            assertEquals(key, normalizePingProtocol(key))
        }
        assertEquals("tcp", normalizePingProtocol("unknown"))
    }

    @Test fun displayDefaultsAreStable() {
        for (key in listOf("numeric", "bars", "both", "dots")) {
            assertEquals(key, normalizePingDisplay(key))
        }
        assertEquals("numeric", normalizePingDisplay("unknown"))
    }

    @Test fun missingFailedAndRunningAreNotSuccessful() {
        assertEquals(0, pingSignalLevel(null, false))
        assertEquals(0, pingSignalLevel(-1, false))
        assertEquals(0, pingSignalLevel(10, true))
        assertEquals(4, pingSignalLevel(0, false))
        assertEquals(4, pingSignalLevel(99, false))
        assertEquals(3, pingSignalLevel(100, false))
        assertEquals(2, pingSignalLevel(200, false))
        assertEquals(1, pingSignalLevel(400, false))
    }

    @Test fun spokenStatusDoesNotDependOnVisualEncoding() {
        assertEquals("Проверка пинга", pingStatusDescription(20, true))
        assertEquals("Пинг ещё не измерен", pingStatusDescription(null, false))
        assertEquals("Ответ не получен или замер недоступен", pingStatusDescription(-1, false))
        assertEquals("Пинг: 0 мс", pingStatusDescription(0, false))
    }
}
