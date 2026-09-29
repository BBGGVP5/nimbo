package com.danila.nimbo.mihomo

import com.danila.nimbo.network.NativeMihomoDocument
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.ui.screens.SubscriptionProfile
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class MihomoSubscriptionTemplatesTest {
    private val yaml = "# exact provider template\r\nproxy-groups: [{name: 'Auto EU', type: url-test, proxies: [DIRECT]}]\r\n"
    private val source = "https://sub.example/token/mihomo?key=a%2Bb"
    private val parent = SubscriptionProfile(url = "https://sub.example/token?key=a%2Bb", name = "My subscription")
    private fun document(id: String = "default", name: String = "Mihomo 1", body: String = yaml) =
        NativeMihomoDocument(name, body, MihomoProtocol.sourceHash(body), id)

    @Test fun formatRoutePreservesEncodedPathAndQueryWithoutDuplicateSuffix() {
        for (suffix in listOf("", "/", "/json", "/json/", "/clash", "/mihomo", "/mihomo/", "/singbox")) {
            assertEquals(source, MihomoSubscriptionFetcher.templateUrl("https://sub.example/token$suffix?key=a%2Bb"))
        }
        assertEquals("https://sub.example/a%2Fb/mihomo?token=x%2Fy", MihomoSubscriptionFetcher.templateUrl("https://sub.example/a%2Fb?token=x%2Fy"))
        assertEquals("https://sub.example/my-json/mihomo", MihomoSubscriptionFetcher.templateUrl("https://sub.example/my-json"))
    }

    @Test fun existingOrdinarySubscriptionFetchesRenderedMihomoWithoutRepeatingXrayRequest() {
        val requests = mutableListOf<String>()
        val result = MihomoSubscriptionFetcher.discover(parent.url, false, { url ->
            requests += url
            yaml
        }, { body -> listOf(document(body = body)) })
        assertEquals(listOf(source), requests)
        assertEquals(source, result.sourceUrl)
        assertEquals(yaml, result.documents.single().source)
    }

    @Test fun pastedOrdinaryLinkFallsThroughToTemplateAndStoresRefreshEndpoint() {
        val requests = mutableListOf<String>()
        val result = MihomoSubscriptionFetcher.discover(parent.url, true, { url ->
            requests += url
            if (url == parent.url) "vless://example" else yaml
        }, { body -> if (body == yaml) listOf(document()) else emptyList() })
        assertEquals(listOf(parent.url, source), requests)
        assertEquals(source, result.sourceUrl)
    }

    @Test fun directYamlUsesExactSourceWithoutInventingASecondUrl() {
        var requests = 0
        val result = MihomoSubscriptionFetcher.discover("https://cdn.example/profile.yaml", true, {
            requests++; yaml
        }, { listOf(document()) })
        assertEquals(1, requests)
        assertEquals("https://cdn.example/profile.yaml", result.sourceUrl)
    }

    @Test fun accessDenialDoesNotProbeAnotherRoute() {
        var requests = 0
        val error = assertThrows(MihomoException::class.java) {
            MihomoSubscriptionFetcher.discover(parent.url, true, {
                requests++
                throw MihomoException("SUBSCRIPTION_ACCESS_DENIED")
            }, { emptyList() })
        }
        assertEquals("SUBSCRIPTION_ACCESS_DENIED", error.code)
        assertEquals(1, requests)
    }

    @Test fun unrelatedResponseIsNotImportedAsAProfile() {
        val error = assertThrows(MihomoException::class.java) {
            MihomoSubscriptionFetcher.discover(source, true, { "<html>subscription page</html>" }, { emptyList() })
        }
        assertEquals("SUBSCRIPTION_MIHOMO_UNAVAILABLE", error.code)
    }

    @Test fun identitySurvivesSameOriginRedirectButDoesNotLeakToCdn() {
        val requests = mutableListOf<Request>()
        val transport = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val request = chain.request()
            requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("fixture")
                .body((if (requests.size == 3) yaml else "").toResponseBody())
                .apply {
                    when (requests.size) {
                        1 -> code(302).header("Location", "/other/mihomo")
                        2 -> code(302).header("Location", "https://cdn.example/template.yaml")
                        else -> code(200)
                    }
                }.build()
        }.build()
        val headers = mapOf("User-Agent" to "Nimbo/1.3.0-beta.1/Android", "x-hwid" to "fixture-device", "x-device-id" to "fixture-device")
        assertEquals(yaml, MihomoSubscriptionFetcher.fetch(source, transport, headers))
        assertEquals(listOf("fixture-device", "fixture-device", null), requests.map { it.header("x-hwid") })
        assertTrue(requests.all { it.header("User-Agent") == headers["User-Agent"] })
    }

    @Test fun refreshKeepsOriginalSubscriptionExactYamlAndStableSelectionIdentity() {
        val templates = MihomoSubscriptionTemplates(source, listOf(document()))
        val first = templates.mergeInto(listOf(parent), parent)
        assertEquals(parent, first.first())
        val native = first.last().copy(customName = "My group")
        assertEquals(yaml, native.rawConfig)
        assertEquals(source, native.mihomoSourceUrl)
        val updated = templates.copy(documents = listOf(document(body = yaml + "mode: rule\n")))
            .mergeInto(listOf(parent, native), parent)
        assertEquals(2, updated.size)
        assertEquals(native.url, updated.last().url)
        assertEquals(native.servers.single().uuid, updated.last().servers.single().uuid)
        assertEquals("My group", updated.last().customName)
        assertEquals(yaml + "mode: rule\n", updated.last().rawConfig)
    }

    @Test fun namedTemplateEnvelopePreservesUnicodeNamesAndStableIds() {
        val envelope = org.json.JSONObject().put("templates", org.json.JSONArray().put(
            org.json.JSONObject().put("name", "🇫🇮 Быстрый маршрут").put("templateUuid", "template-fi").put("yaml", yaml)
        ).put(org.json.JSONObject().put("name", "Резерв • LTE").put("templateUuid", "template-lte").put("yaml", yaml + "mode: rule\n"))).toString()
        val documents = SubscriptionManager.detectNativeMihomoDocuments(envelope) { it.startsWith("# exact") }
        assertEquals(listOf("🇫🇮 Быстрый маршрут", "Резерв • LTE"), documents.map { it.name })
        assertEquals(listOf("template-fi", "template-lte"), documents.map { it.documentId })
        val profiles = MihomoSubscriptionTemplates(source, documents).mergeInto(listOf(parent), parent)
        assertEquals(3, profiles.size)
        assertEquals(2, profiles.drop(1).map { it.url }.distinct().size)
    }

    @Test fun mirrorChangeUpdatesSourceWithoutDuplicatingNativeProfile() {
        val first = MihomoSubscriptionTemplates(source, listOf(document())).mergeInto(listOf(parent), parent)
        val native = first.last()
        val mirror = "https://mirror.example/token/mihomo?key=a%2Bb"
        val next = MihomoSubscriptionTemplates(mirror, listOf(document(body = yaml + "mode: rule\n")))
            .mergeInto(first, parent)
        assertEquals(2, next.size)
        assertEquals(native.url, next.last().url)
        assertEquals(native.servers.single().uuid, next.last().servers.single().uuid)
        assertEquals(parent.url, next.last().mihomoParentUrl)
        assertEquals(mirror, next.last().mihomoSourceUrl)
    }

    @Test fun twoLinksResolvingToSameEndpointDoNotDuplicateProfileIdentity() {
        val templates = MihomoSubscriptionTemplates(source, listOf(document()))
        val first = templates.mergeInto(listOf(parent), parent)
        val alias = parent.copy(url = "https://sub.example/token/json?key=a%2Bb")
        val next = templates.mergeInto(first + alias, alias)
        assertEquals(3, next.size)
        assertEquals(1, next.count(MihomoProfiles::isMihomo))
        assertEquals(next.size, next.map { it.url }.distinct().size)
        assertEquals(first.last().url, next.single(MihomoProfiles::isMihomo).url)
    }

    @Test fun emptyOrProtectedRefreshRetainsLastKnownConfiguration() {
        val original = MihomoSubscriptionTemplates(source, listOf(document())).mergeInto(listOf(parent), parent)
        assertEquals(original, MihomoSubscriptionTemplates(source, emptyList()).mergeInto(original, parent))
        val next = MihomoSubscriptionTemplates(source, listOf(document(body = yaml + "mode: rule\n")))
        assertEquals(original, next.mergeInto(original, parent, setOf(original.last().url)))
    }
}
