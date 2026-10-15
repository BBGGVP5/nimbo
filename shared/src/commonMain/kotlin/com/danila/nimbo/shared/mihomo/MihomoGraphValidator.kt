package com.danila.nimbo.shared.mihomo

enum class MihomoIssueCode {
    EMPTY_NAME, EMPTY_TYPE, DUPLICATE_NAME, RESERVED_NAME,
    MISSING_PROXY, MISSING_PROVIDER, EMPTY_GROUP, GROUP_CYCLE,
    INVALID_EMPTY_FALLBACK, PROVIDER_GROUP_COLLISION,
    UNSUPPORTED_GROUP_TYPE, UNSUPPORTED_PROVIDER_TYPE, UNSUPPORTED_FEATURE,
    EMPTY_DOCUMENT, INCOMPATIBLE_CORE,
}

data class MihomoIssue(
    val code: MihomoIssueCode,
    val path: String,
    val references: List<String> = emptyList(),
)

/**
 * Bounded preflight against v1.19.32, NOT native configuration validation. Does not resolve
 * providers, expand dynamic groups, check protocol options, rules, DNS or dialer-proxy graphs.
 * An empty issue list never establishes that a configuration can run.
 */
object MihomoGraphValidator {
    const val upstreamVersion: String = "v1.19.32"
    const val upstreamCommit: String = "88dcbf7f1614a67c3b36b848ee3592dfa92ada36"

    // GLOBAL is intentionally absent: upstream permits an explicitly declared GLOBAL group.
    private val policies = setOf("DIRECT", "REJECT", "REJECT-DROP", "COMPATIBLE", "PASS", "PASS-RULE")
    private val groupTypes = setOf("select", "url-test", "fallback", "load-balance")
    private val providerTypes = setOf("http", "file", "inline")

    fun validate(graph: MihomoGraph): List<MihomoIssue> {
        val issues = mutableListOf<MihomoIssue>()
        val proxyNames = mutableSetOf<String>()
        val providerNames = mutableSetOf<String>()
        fun name(value: String, path: String, namespace: MutableSet<String>, reserved: Set<String>) {
            if (value.isBlank()) issues += MihomoIssue(MihomoIssueCode.EMPTY_NAME, path)
            if (value in reserved) issues += MihomoIssue(MihomoIssueCode.RESERVED_NAME, path, listOf(value))
            if (!namespace.add(value)) issues += MihomoIssue(MihomoIssueCode.DUPLICATE_NAME, path, listOf(value))
        }
        graph.proxies.forEachIndexed { index, proxy ->
            name(proxy.name, "proxies[$index].name", proxyNames, policies)
            if (proxy.type.isBlank()) issues += MihomoIssue(MihomoIssueCode.EMPTY_TYPE, "proxies[$index].type")
        }
        graph.groups.forEachIndexed { index, group ->
            name(group.name, "proxy-groups[$index].name", proxyNames, policies)
            if (group.type !in groupTypes) {
                issues += MihomoIssue(MihomoIssueCode.UNSUPPORTED_GROUP_TYPE, "proxy-groups[$index].type", listOf(group.type))
            }
        }
        graph.providers.forEachIndexed { index, provider ->
            name(provider.name, "proxy-providers[$index].name", providerNames, setOf("default"))
            if (provider.type !in providerTypes) {
                issues += MihomoIssue(MihomoIssueCode.UNSUPPORTED_PROVIDER_TYPE, "proxy-providers[$index].type", listOf(provider.type))
            }
        }
        val groupNames = graph.groups.map { it.name }.toSet()
        // Upstream can also create compatible providers for groups with static members.
        // Their availability depends on native parsing order/filter expansion, so do not
        // falsely label them missing. Native validation must resolve these advanced uses.
        val possibleSyntheticProviders = graph.groups
            .filter { it.proxies.isNotEmpty() || it.includesAllProxies }
            .map { it.name }.toSet()
        graph.groups.forEachIndexed { index, group ->
            val path = "proxy-groups[$index]"
            group.proxies.forEachIndexed { memberIndex, member ->
                if (member !in proxyNames && member !in policies) {
                    issues += MihomoIssue(MihomoIssueCode.MISSING_PROXY, "$path.proxies[$memberIndex]", listOf(member))
                }
            }
            // Upstream replaces `use`, rather than appending, when either flag is true.
            if (!group.includesAllProviders) {
                group.use.forEachIndexed { providerIndex, provider ->
                    if (provider !in providerNames && provider !in possibleSyntheticProviders) {
                        issues += MihomoIssue(MihomoIssueCode.MISSING_PROVIDER, "$path.use[$providerIndex]", listOf(provider))
                    }
                }
            }
            val hasProviders = if (group.includesAllProviders) providerNames.isNotEmpty() else group.use.isNotEmpty()
            // include-all-proxies may legitimately resolve to the native empty-fallback policy.
            if (group.proxies.isEmpty() && !hasProviders && !group.includesAllProxies) {
                issues += MihomoIssue(MihomoIssueCode.EMPTY_GROUP, path)
            }
            if (group.name in providerNames && group.proxies.isNotEmpty()) {
                issues += MihomoIssue(MihomoIssueCode.PROVIDER_GROUP_COLLISION, "$path.name", listOf(group.name))
            }
            val fallback = group.emptyFallback?.takeUnless { it.isEmpty() } ?: "COMPATIBLE"
            if (fallback in groupNames || (fallback !in proxyNames && fallback !in policies)) {
                issues += MihomoIssue(MihomoIssueCode.INVALID_EMPTY_FALLBACK, "$path.empty-fallback", listOf(fallback))
            }
        }
        issues += cycles(graph.groups)
        return issues
    }

    /** Iterative DFS: a deeply nested imported graph must not overflow the platform stack. */
    private fun cycles(groups: List<MihomoGroup>): List<MihomoIssue> {
        val byName = groups.associateBy { it.name }
        val indices = groups.mapIndexed { index, group -> group.name to index }.toMap()
        val state = mutableMapOf<String, Int>()
        val issues = mutableListOf<MihomoIssue>()
        for (root in byName.keys) {
            if (state[root] != null) continue
            val path = mutableListOf(root)
            val iterators = mutableListOf(byName.getValue(root).proxies.withIndex().iterator())
            state[root] = 1
            while (path.isNotEmpty()) {
                val iterator = iterators.last()
                if (!iterator.hasNext()) {
                    state[path.removeAt(path.lastIndex)] = 2
                    iterators.removeAt(iterators.lastIndex)
                    continue
                }
                val (memberIndex, next) = iterator.next()
                if (next !in byName) continue
                when (state[next]) {
                    1 -> issues += MihomoIssue(
                        MihomoIssueCode.GROUP_CYCLE,
                        "proxy-groups[${indices.getValue(path.last())}].proxies[$memberIndex]",
                        path.subList(path.indexOf(next), path.size).toList() + next,
                    )
                    null -> {
                        state[next] = 1
                        path += next
                        iterators += byName.getValue(next).proxies.withIndex().iterator()
                    }
                }
            }
        }
        return issues
    }
}
