package com.danila.nimbo.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NimboPingPresentationTest {
    @Test fun nimboEstimateIsOnlyAPresentationTransform() {
        assertEquals(300, pingDisplayValue(990, "nimbo"))
        assertEquals(420, pingDisplayValue(1387, "nimbo"))
        assertEquals(0, pingDisplayValue(0, "nimbo"))
        assertEquals(-1, pingDisplayValue(-1, "nimbo"))
        assertEquals(null, pingDisplayValue(null, "nimbo"))
        for (method in listOf("tcp", "http", "http_get", "http_head", "icmp")) {
            assertEquals(990, pingDisplayValue(990, method))
            assertEquals("990 ms", pingDisplayLabel(990, false, method))
        }
        assertEquals("≈300 ms", pingDisplayLabel(990, false, "nimbo"))
        assertEquals("≈0 ms", pingDisplayLabel(0, false, "nimbo"))
        assertEquals("×", pingDisplayLabel(-1, false, "nimbo"))
        assertEquals("— ms", pingDisplayLabel(null, false, "nimbo"))
        assertEquals("…", pingDisplayLabel(990, true, "nimbo"))
        assertEquals(2, pingSignalLevel(pingDisplayValue(990, "nimbo"), false))
    }

    @Test fun estimatedSpokenStatusIsExplicitAndMissingResultsStayMissing() {
        assertEquals("Оценка пинга: примерно 300 мс", pingStatusDescription(990, false, "nimbo"))
        assertEquals("Пинг: 990 мс", pingStatusDescription(990, false, "http_get"))
        assertEquals("Ответ не получен или замер недоступен", pingStatusDescription(-1, false, "nimbo"))
    }

    @Test fun legacyProtocolKeepsItsHttpMethod() {
        assertEquals("http_head", normalizePingProtocol("http"))
        for (key in listOf("nimbo", "tcp", "http_get", "http_head", "icmp")) {
            assertEquals(key, normalizePingProtocol(key))
        }
        assertEquals("nimbo", normalizePingProtocol("unknown"))
        assertEquals("nimbo", normalizePingProtocol(""))
    }

    @Test fun nimboIsTheDefaultWithoutOverwritingExplicitTcp() {
        assertEquals("nimbo", NimboUiState().pingProtocol)
        assertEquals("tcp", normalizePingProtocol("tcp"))
    }

    @Test fun displayDefaultsAreStable() {
        for (key in listOf("numeric", "bars", "both", "dots")) {
            assertEquals(key, normalizePingDisplay(key))
        }
        assertEquals("numeric", normalizePingDisplay("unknown"))
    }

    @Test fun completedRowsStopSpinningWhileOtherServersAreStillPending() {
        val pending = setOf("one", "two", "three")
        assertEquals(setOf("two", "three"), remainingPingIds(pending, listOf("one"), true))
        assertEquals(pending, remainingPingIds(pending, listOf("unrelated"), true))
        assertEquals(emptySet(), remainingPingIds(pending, emptyList(), false))
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
