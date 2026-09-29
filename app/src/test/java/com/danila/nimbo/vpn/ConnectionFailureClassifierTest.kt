package com.danila.nimbo.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFailureClassifierTest {

    @Test
    fun tunFailureKeepsPreciseReasonFromTheBuilder() {
        val failure = ConnectionFailureClassifier.classify(
            "Failed to establish TUN: разрешение на VPN не выдано или отозвано системой"
        )
        assertTrue(failure.reason.contains("разрешение на VPN не выдано"))
        assertTrue(failure.nextStep.isNotBlank())
    }

    @Test
    fun tunFailureUsesConflictHintAsNextStep() {
        val failure = ConnectionFailureClassifier.classify(
            raw = "Failed to establish TUN: система вернула пустой дескриптор без объяснения",
            tunConflictHint = "Обнаружен активный VPN-профиль."
        )
        assertEquals("Обнаружен активный VPN-профиль.", failure.nextStep)
    }

    @Test
    fun missingNetworkWinsOverEverythingElse() {
        val failure = ConnectionFailureClassifier.classify("timeout", hasNetwork = false)
        assertEquals("Нет доступа к сети", failure.reason)
    }

    @Test
    fun dnsFailureIsRecognized() {
        val failure = ConnectionFailureClassifier.classify("Unable to resolve host \"sub.example.com\"")
        assertTrue(failure.reason.contains("DNS"))
    }

    @Test
    fun handshakeFailureIsRecognized() {
        val failure = ConnectionFailureClassifier.classify("REALITY handshake failed")
        assertTrue(failure.reason.contains("рукопожатие"))
    }

    @Test
    fun busyPortIsRecognized() {
        val failure = ConnectionFailureClassifier.classify("bind: address already in use")
        assertTrue(failure.reason.contains("порт"))
    }

    @Test
    fun unknownErrorStillGivesReasonAndNextStep() {
        val failure = ConnectionFailureClassifier.classify("что-то пошло не так")
        assertTrue(failure.reason.isNotBlank())
        assertTrue(failure.nextStep.isNotBlank())
    }

    @Test
    fun emptyErrorIsExplainedInsteadOfBlank() {
        val failure = ConnectionFailureClassifier.classify(null)
        assertEquals("причина не сообщена ядром", failure.technical)
    }

    @Test
    fun sensitiveDataNeverLeaksIntoTheReport() {
        val failure = ConnectionFailureClassifier.classify(
            "dial tcp 203.0.113.10:443 failed for https://sub.example.com/api/sub/" +
                "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        )
        val technical = failure.technical
        assertFalse(technical.contains("203.0.113.10"))
        assertFalse(technical.contains("sub.example.com"))
        assertFalse(technical.contains("3f2504e0"))
    }
}
