package com.danila.nimbo.utils

import com.danila.nimbo.model.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPolicyManagerTest {

    @Test
    fun isBypassServer_doesNotTreatAutobalancersTemplateAsBypass() {
        val server = remoteServer(name = "Любой сервер", templateName = "autobalancers2")

        assertFalse(isBypassServer(server))
        assertTrue(isAutoBalancerServer(server))
    }

    @Test
    fun isAutoBalancerServer_detectsAutobalancersByServerName() {
        val server = remoteServer(name = "autobalancers4")

        assertTrue(isAutoBalancerServer(server))
    }

    @Test
    fun isAutoBalancerServer_detectsRemnawaveLeastPingTag() {
        val server = remoteServer(name = "Маршрут EU").copy(
            remoteBalancerTag = "balance-last-ping"
        )

        assertTrue(isAutoBalancerServer(server))
        assertFalse(isBypassServer(server))
    }

    @Test
    fun isAutoBalancerServer_detectsAutobalancersInServerDescription() {
        val server = remoteServer(
            name = "EU обход",
            serverDescription = "template: autobalancers2"
        )

        assertTrue(isAutoBalancerServer(server))
        assertFalse(isBypassServer(server))
    }

    @Test
    fun isAutoBalancerServer_detectsRussianAutoBalancerNameForRemoteTemplateServer() {
        val server = remoteServer(
            name = "Автобалансер EU | Обход #1",
            templateUuid = "18f4fdb8-6d55-4d80-8b9b-90d2bca8a93a"
        )

        assertTrue(isAutoBalancerServer(server))
        assertFalse(isBypassServer(server))
    }

    @Test
    fun isAutoBalancerServer_detectsRussianAutoBalancerBypassNameWithoutTemplateHints() {
        val server = remoteServer(
            name = "Автобалансер EU | Обход #4",
            host = "google.com",
            port = 5443
        )

        assertTrue(isAutoBalancerServer(server))
        assertFalse(isBypassServer(server))
    }

    @Test
    fun filterServersForPolicies_keepsRuAndAutoBalancersInBypassOnlyMode() {
        val autoByTemplate = remoteServer(name = "Remote A", host = "auto1.example", port = 8443, templateName = "autobalancers")
        val autoByTemplate2 = remoteServer(name = "Remote B", host = "auto2.example", port = 9443, templateName = "autobalancers3")
        val ruDomain = remoteServer(name = "RU обход", host = "ya.nebulaguard.ru")
        val nonRuBypass = remoteServer(name = "US обход", host = "google.com")
        val nonMatchingBalancerWord = remoteServer(name = "Автобаланс обход", host = "nonru.example", port = 7443)

        val filtered = filterServersForPolicies(
            servers = listOf(autoByTemplate, autoByTemplate2, ruDomain, nonRuBypass, nonMatchingBalancerWord),
            autoBypassByNetwork = true,
            networkType = ActiveNetworkType.WIFI,
            shouldUseBypassOnly = true
        )

        assertEquals(3, filtered.size)
        assertTrue(filtered.contains(ruDomain))
        assertTrue(filtered.contains(autoByTemplate))
        assertTrue(filtered.contains(autoByTemplate2))
        assertFalse(filtered.contains(nonRuBypass))
        assertFalse(filtered.contains(nonMatchingBalancerWord))
    }

    @Test
    fun isBypassServer_doesNotTreatBypassWordInNameAsBypass() {
        val server = remoteServer(name = "EU | Обход", host = "google.com", port = 443)

        assertFalse(isBypassServer(server))
        assertFalse(isAutoBalancerServer(server))
    }

    private fun remoteServer(
        name: String,
        templateName: String? = null,
        host: String = "API",
        port: Int = 0,
        serverDescription: String? = null,
        templateUuid: String? = null
    ): Server = Server(
        name = name,
        host = host,
        port = port,
        uuid = "remote",
        protocol = "xray",
        profileUrl = "https://example.com/sub",
        templateName = templateName,
        serverDescription = serverDescription,
        templateUuid = templateUuid
    )
}
