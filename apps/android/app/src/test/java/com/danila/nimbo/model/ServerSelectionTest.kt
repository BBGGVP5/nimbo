package com.danila.nimbo.model

import com.danila.nimbo.utils.NetworkProfileManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSelectionTest {

    @Test
    fun matchesSelection_remoteTemplatesWithDifferentTemplateUuidAreNotEqual() {
        val a = remoteServer(templateUuid = "uuid-a", templateName = "autobalancers")
        val b = remoteServer(templateUuid = "uuid-b", templateName = "autobalancers2")

        assertFalse(a.matchesSelection(b))
        assertNotEquals(a.selectionKey(), b.selectionKey())
    }

    @Test
    fun matchesSelection_remoteTemplatesWithSameTemplateUuidAreEqual() {
        val a = remoteServer(templateUuid = "same-uuid", templateName = "autobalancers")
        val b = remoteServer(templateUuid = "same-uuid", templateName = "autobalancers2")

        assertTrue(a.matchesSelection(b))
    }

    @Test
    fun networkProfileKey_includesRemoteTemplateHints() {
        val a = remoteServer(templateUuid = "uuid-a", templateName = "autobalancers")
        val b = remoteServer(templateUuid = "uuid-b", templateName = "autobalancers2")

        val keyA = NetworkProfileManager.buildServerKey(a)
        val keyB = NetworkProfileManager.buildServerKey(b)
        assertNotEquals(keyA, keyB)
    }

    private fun remoteServer(
        templateUuid: String,
        templateName: String
    ): Server = Server(
        name = "Автобалансер EU",
        host = "API",
        port = 0,
        uuid = "remote",
        protocol = "xray",
        profileUrl = "https://example.com/sub",
        templateUuid = templateUuid,
        templateName = templateName
    )
}
