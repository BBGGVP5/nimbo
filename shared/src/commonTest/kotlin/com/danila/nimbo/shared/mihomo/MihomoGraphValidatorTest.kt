package com.danila.nimbo.shared.mihomo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MihomoGraphValidatorTest {
    private fun codes(graph: MihomoGraph) = MihomoGraphValidator.validate(graph).map { it.code }

    @Test
    fun nestedGroupsAndBuiltinPoliciesAreReferencesNotServers() {
        val graph = MihomoGraph(
            proxies = listOf(MihomoProxy("node", "vless")),
            groups = listOf(
                MihomoGroup("GLOBAL", "select", listOf("auto", "DIRECT", "REJECT")),
                MihomoGroup("auto", "url-test", listOf("node", "REJECT-DROP", "COMPATIBLE", "PASS", "PASS-RULE")),
            ),
        )
        assertTrue(codes(graph).isEmpty())
        assertEquals(listOf("auto", "DIRECT", "REJECT"), graph.groups.first().proxies)
    }

    @Test
    fun reservedPoliciesCannotBeRedeclaredButNamesAreCaseSensitive() {
        for (reserved in listOf("DIRECT", "REJECT", "REJECT-DROP", "COMPATIBLE", "PASS", "PASS-RULE")) {
            assertTrue(MihomoIssueCode.RESERVED_NAME in codes(MihomoGraph(proxies = listOf(MihomoProxy(reserved, "direct")))))
            assertTrue(MihomoIssueCode.RESERVED_NAME in codes(MihomoGraph(groups = listOf(MihomoGroup(reserved, "select", listOf("DIRECT"))))))
        }
        assertTrue(codes(MihomoGraph(proxies = listOf(MihomoProxy("direct", "direct")))).isEmpty())
    }

    @Test
    fun detectsSelfAndIndirectCyclesWithClosedPath() {
        val self = MihomoGraphValidator.validate(MihomoGraph(groups = listOf(MihomoGroup("a", "select", listOf("a")))))
        assertEquals(listOf("a", "a"), self.single { it.code == MihomoIssueCode.GROUP_CYCLE }.references)
        val indirect = MihomoGraphValidator.validate(MihomoGraph(groups = listOf(
            MihomoGroup("a", "select", listOf("b")),
            MihomoGroup("b", "fallback", listOf("c")),
            MihomoGroup("c", "select", listOf("a")),
        )))
        assertEquals(listOf("a", "b", "c", "a"), indirect.single { it.code == MihomoIssueCode.GROUP_CYCLE }.references)
    }

    @Test
    fun sharedChildIsNotACycleAndDeepGraphsDoNotUseRecursion() {
        val groups = (0 until 10_000).map { index ->
            MihomoGroup("g$index", "select", listOf(if (index == 9_999) "DIRECT" else "g${index + 1}"))
        } + MihomoGroup("other", "select", listOf("g5000"))
        assertTrue(codes(MihomoGraph(groups = groups)).isEmpty())
    }

    @Test
    fun missingProxyAndProviderUseDifferentNamespaces() {
        val graph = MihomoGraph(
            proxies = listOf(MihomoProxy("node", "vless")),
            providers = listOf(MihomoProxyProvider("feed", "http")),
            groups = listOf(MihomoGroup("choice", "select", listOf("feed", "missing"), use = listOf("node", "absent"))),
        )
        val issues = MihomoGraphValidator.validate(graph)
        assertEquals(2, issues.count { it.code == MihomoIssueCode.MISSING_PROXY })
        assertEquals(2, issues.count { it.code == MihomoIssueCode.MISSING_PROVIDER })
        assertEquals("proxy-groups[0].proxies[0]", issues.first().path)
    }

    @Test
    fun providerOnlyGroupsDoNotNeedFlattenedServers() {
        val graph = MihomoGraph(
            providers = listOf("http", "file", "inline").map { MihomoProxyProvider(it, it) },
            groups = listOf(MihomoGroup("choice", "load-balance", use = listOf("http", "file", "inline"))),
        )
        assertTrue(codes(graph).isEmpty())
        assertTrue(graph.proxies.isEmpty())
        assertTrue(graph.groups.single().proxies.isEmpty())
    }

    @Test
    fun syntheticGroupProvidersAreDeferredToNativeValidationWithoutInventingDeclarations() {
        val graph = MihomoGraph(groups = listOf(
            MihomoGroup("static", "select", listOf("DIRECT")),
            MihomoGroup("consumer", "select", use = listOf("static")),
        ))
        assertTrue(codes(graph).isEmpty())
        assertTrue(graph.providers.isEmpty())
        assertEquals(listOf("static"), graph.groups.last().use)
        assertEquals(
            MihomoPreflightDecision.REQUIRES_NATIVE_VALIDATION,
            MihomoPreflight.inspect(MihomoDocument("source", graph), NimboCore.MIHOMO).decision,
        )
    }

    @Test
    fun dynamicFlagsOverrideUseWithoutMutatingItOrEvaluatingFilters() {
        for (group in listOf(
            MihomoGroup("auto", "url-test", use = listOf("old-feed"), includeAll = true),
            MihomoGroup("auto", "url-test", use = listOf("old-feed"), includeAllProviders = true),
        )) {
            val dynamic = group.copy(filter = "(?<=region-)hk`jp", excludeFilter = "blocked", excludeType = "Http|Socks5")
            val graph = MihomoGraph(groups = listOf(dynamic), providers = listOf(MihomoProxyProvider("feed", "http")))
            assertTrue(codes(graph).isEmpty())
            assertEquals(dynamic, graph.groups.single())
            assertEquals(listOf("old-feed"), graph.groups.single().use)
            assertEquals("(?<=region-)hk`jp", graph.groups.single().filter)
        }
        val includeProxies = MihomoGroup("all", "select", includeAllProxies = true)
        assertTrue(codes(MihomoGraph(groups = listOf(includeProxies))).isEmpty())
        assertTrue(MihomoIssueCode.MISSING_PROVIDER in codes(MihomoGraph(groups = listOf(includeProxies.copy(use = listOf("missing"))))))
    }

    @Test
    fun emptyGroupsFollowIncludeAllFallbackSemantics() {
        assertTrue(MihomoIssueCode.EMPTY_GROUP in codes(MihomoGraph(groups = listOf(MihomoGroup("a", "select")))))
        assertTrue(MihomoIssueCode.EMPTY_GROUP in codes(MihomoGraph(groups = listOf(MihomoGroup("a", "select", includeAllProviders = true)))))
        assertTrue(codes(MihomoGraph(groups = listOf(MihomoGroup("a", "select", includeAll = true)))).isEmpty())
    }

    @Test
    fun duplicateNamesAndProviderReservedNameAreRejected() {
        val graph = MihomoGraph(
            proxies = listOf(MihomoProxy("a", "vless"), MihomoProxy("a", "trojan")),
            groups = listOf(MihomoGroup("a", "select", listOf("DIRECT"))),
            providers = listOf(MihomoProxyProvider("default", "inline"), MihomoProxyProvider("default", "inline")),
        )
        assertEquals(3, codes(graph).count { it == MihomoIssueCode.DUPLICATE_NAME })
        assertTrue(MihomoIssueCode.RESERVED_NAME in codes(graph))
    }

    @Test
    fun providerNamesMayMatchProxiesButCannotCollideWithStaticGroupProvider() {
        val provider = MihomoProxyProvider("a", "inline")
        assertTrue(codes(MihomoGraph(proxies = listOf(MihomoProxy("a", "direct")), providers = listOf(provider))).isEmpty())
        assertTrue(MihomoIssueCode.PROVIDER_GROUP_COLLISION in codes(MihomoGraph(
            providers = listOf(provider), groups = listOf(MihomoGroup("a", "select", listOf("DIRECT"))),
        )))
    }

    @Test
    fun fallbackMustResolveToProxyOrBuiltinNotGroup() {
        val group = MihomoGroup("a", "select", listOf("DIRECT"))
        assertTrue(MihomoIssueCode.INVALID_EMPTY_FALLBACK in codes(MihomoGraph(groups = listOf(group.copy(emptyFallback = "a")))))
        assertTrue(MihomoIssueCode.INVALID_EMPTY_FALLBACK in codes(MihomoGraph(groups = listOf(group.copy(emptyFallback = "absent")))))
        assertTrue(codes(MihomoGraph(groups = listOf(group.copy(emptyFallback = "REJECT")))).isEmpty())
    }

    @Test
    fun unsupportedTypesRemainInGraphIncludingRemovedRelay() {
        val graph = MihomoGraph(
            groups = listOf(MihomoGroup("chain", "relay", listOf("DIRECT")), MihomoGroup("future", "future-type", listOf("DIRECT"))),
            providers = listOf(MihomoProxyProvider("feed", "future-vehicle")),
        )
        assertEquals(2, codes(graph).count { it == MihomoIssueCode.UNSUPPORTED_GROUP_TYPE })
        assertTrue(MihomoIssueCode.UNSUPPORTED_PROVIDER_TYPE in codes(graph))
        assertEquals("relay", graph.groups.first().type)
        assertFalse(graph.providers.isEmpty())
    }
}
