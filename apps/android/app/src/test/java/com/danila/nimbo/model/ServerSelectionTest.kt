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

    @Test
    fun remoteBalancerTagsRemainSeparateSelections() {
        val lastPing = Server(
            name = "leastPing",
            host = "API",
            port = 0,
            uuid = "remote",
            protocol = "xray",
            profileUrl = "https://example.com/sub",
            remoteBalancerTag = "balance-last-ping"
        )
        val leastLoaded = lastPing.copy(
            name = "leastLoad",
            remoteBalancerTag = "balance-least-loaded"
        )

        assertFalse(lastPing.matchesSelection(leastLoaded))
        assertNotEquals(lastPing.selectionKey(), leastLoaded.selectionKey())
        assertNotEquals(lastPing.pingKey(), leastLoaded.pingKey())
    }

    @Test
    fun pingMeasurementsRemainIndependentForNamedNodesOnSameEndpoint() {
        val cdnA = Server(
            name = "CDN A",
            host = "shared.example.com",
            port = 443,
            uuid = "shared-user-id",
            protocol = "vless",
            profileUrl = "https://example.com/sub",
            network = "tcp"
        )
        val cdnB = cdnA.copy(name = "CDN B")

        assertNotEquals(cdnA.pingMeasurementKey(), cdnB.pingMeasurementKey())
    }

    @Test
    fun remoteTemplatesWithDifferentRoutesHaveIndependentPingMeasurements() {
        val routeA = remoteServer(templateUuid = "route-a", templateName = "CDN A")
        val routeB = remoteServer(templateUuid = "route-b", templateName = "CDN B")

        assertNotEquals(routeA.pingMeasurementKey(), routeB.pingMeasurementKey())
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
