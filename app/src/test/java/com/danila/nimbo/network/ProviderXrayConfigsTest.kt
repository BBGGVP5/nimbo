package com.danila.nimbo.network

import com.danila.nimbo.model.Server
import com.danila.nimbo.ui.screens.SubscriptionProfile
import com.danila.nimbo.ui.screens.SubscriptionTemplateCache
import com.danila.nimbo.utils.isAutoBalancerServer
import com.danila.nimbo.utils.isNoticePlaceholderServer
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderXrayConfigsTest {
    private val profileUrl = "https://subscription.example/public-fixture"
    private fun payload(): String = checkNotNull(javaClass.getResource("/subscriptions/provider-balancers.json")).readText()

    @Test
    fun mixedPayloadRetainsProviderRoutesWithoutPromotingNoticeOrNimboAuto() {
        val text = payload()
        val template = ProviderXrayConfigs.templatesFromArray(text).single()
        val routes = ProviderXrayConfigs.routeServers(template.config, profileUrl)
        assertEquals(listOf("provider-fast", "provider-load"), routes.map { it.remoteBalancerTag })
        assertTrue(routes.all { isAutoBalancerServer(it) && !isNoticePlaceholderServer(it) })
        assertTrue(routes.all { it.name.contains("🇫🇮 Provider Europe") })
        assertTrue(routes.all { it.serverDescription == "Provider choice · WiFi + LTE" })
        assertTrue(routes.all { it.ping == null && it.pingTimestamp == null })

        val direct = SubscriptionManager.parseServerLinksFromClientJsonConfig(text).map(LinkParser::parse)
        assertEquals(2, direct.size)
        assertEquals(1, direct.count(::isNoticePlaceholderServer))
        assertEquals("node.example", direct.single { !isNoticePlaceholderServer(it) }.host)
        assertFalse(isAutoBalancerServer(Server("Nimbo Auto", "node.example", 443, "fixture", "vless")))
    }

    @Test
    fun fullTransportRoutingAndDescriptionsSurviveProfilePersistence() {
        val original = JSONArray(payload()).getJSONObject(0)
        val template = ProviderXrayConfigs.templatesFromArray(payload()).single()
        val routes = ProviderXrayConfigs.routeServers(template.config, profileUrl).map {
            it.copy(templateUuid = template.uuid)
        }
        val direct = SubscriptionManager.parseServerLinksFromClientJsonConfig(payload())
            .map(LinkParser::parse).filterNot(::isNoticePlaceholderServer)
        val profile = SubscriptionProfile(
            url = profileUrl, servers = routes + direct,
            templates = listOf(SubscriptionTemplateCache(template.uuid, template.name, template.templateType, 0, config = template.config))
        )
        val restored = Gson().fromJson(Gson().toJson(profile), SubscriptionProfile::class.java)
        val persistedTemplate = restored.templates.single()
        assertEquals(com.google.gson.JsonParser.parseString(original.toString()), com.google.gson.JsonParser.parseString(persistedTemplate.config!!))
        assertEquals(profile.servers, restored.servers)
        assertTrue(restored.servers.take(2).all { it.templateUuid == persistedTemplate.uuid })
        assertFalse(restored.servers[0].matchesSelection(restored.servers[1]))
        assertEquals("Provider notes: fallback to gRPC if necessary", restored.servers.last().serverDescription)
    }

    @Test
    fun templateIdentityIsStableAcrossReorderingAndSeparateForDifferentConfigs() {
        val config = JSONArray(payload()).getJSONObject(0)
        val other = JSONObject(config.toString()).put("serverDescription", "Same title, different config")
        val first = ProviderXrayConfigs.templatesFromArray(JSONArray().put(config).put(other).toString())
        val reversed = ProviderXrayConfigs.templatesFromArray(JSONArray().put(other).put(config).toString())
        assertEquals(2, first.map { it.uuid }.distinct().size)
        assertEquals(first.map { it.uuid }, reversed.reversed().map { it.uuid })
    }

    @Test
    fun sameNamedConfigsWithSameTagsAndDifferentEndpointsSurvivePolicyDeduplication() {
        val config = JSONArray(payload()).getJSONObject(0)
        val other = JSONObject(config.toString())
        other.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
            .getJSONArray("vnext").getJSONObject(0).put("address", "different-edge.example")
        val templates = ProviderXrayConfigs.templatesFromArray(JSONArray().put(config).put(other).toString())
        val rows = templates.map { template ->
            ProviderXrayConfigs.routeServers(template.config, profileUrl).first()
                .copy(templateUuid = template.uuid)
        }

        assertEquals(rows[0].name, rows[1].name)
        assertEquals(rows[0].remoteBalancerTag, rows[1].remoteBalancerTag)
        assertNotEquals(rows[0].pingKey(), rows[1].pingKey())
        assertEquals(2, com.danila.nimbo.utils.filterServersForPolicies(
            rows, autoBypassByNetwork = true,
            networkType = com.danila.nimbo.utils.ActiveNetworkType.MOBILE,
            shouldUseBypassOnly = true
        ).size)
    }

    @Test
    fun unnamedBalancingArrayDoesNotFlattenMembers() {
        val config = JSONArray(payload()).getJSONObject(0).apply { remove("remarks") }
        val text = JSONArray().put(config).toString()
        assertEquals(1, ProviderXrayConfigs.templatesFromArray(text).size)
        assertTrue(SubscriptionManager.parseServerLinksFromClientJsonConfig(text).isEmpty())
    }

    @Test
    fun plainNoticeAndEmptyPayloadCannotProduceProviderRoutes() {
        val notice = JSONArray(payload()).getJSONObject(2)
        assertTrue(ProviderXrayConfigs.templatesFromArray(JSONArray().put(notice).toString()).isEmpty())
        assertTrue(ProviderXrayConfigs.routeServers(notice.toString(), profileUrl).isEmpty())
        assertTrue(ProviderXrayConfigs.routeServers(null, profileUrl).isEmpty())
        assertTrue(ProviderXrayConfigs.routeServers("{}", profileUrl).isEmpty())
        assertTrue(ProviderXrayConfigs.templatesFromArray("[]").isEmpty())
    }

    @Test
    fun outboundDescriptionIsNotDroppedByJsonToLinkConversionOrTransportWords() {
        val config = JSONArray(payload()).getJSONObject(1)
        config.remove("serverDescription")
        config.getJSONArray("outbounds").getJSONObject(0)
            .put("serverDescription", "News + gRPC fallback available")
        val server = LinkParser.parse(SubscriptionManager.parseServerLinksFromClientJsonConfig(config.toString()).single())
        assertEquals("News + gRPC fallback available", server.serverDescription)
        assertEquals("tcp", server.network)
    }
}

