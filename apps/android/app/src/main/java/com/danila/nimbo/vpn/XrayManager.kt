package com.danila.nimbo.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.model.RoutingProfile
import com.danila.nimbo.model.Server
import com.danila.nimbo.network.RemnawaveApiClient
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libXray.DialerController
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.io.File

object XrayManager {

    private const val TAG = "XrayManager"

    var isConnected = false
        private set

    var connectionError: String? = null
        private set

    private var tunInterface: ParcelFileDescriptor? = null

    suspend fun connect(
        context: Context,
        server: Server,
        vpnService: VpnService,
        overrideConfig: String? = null,
        proxyServers: List<Server> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            NebulaGuardApplication.ensureXrayCoreLoaded()

            val vpnFd = establishTun(vpnService)

            LibXray.registerDialerController(object : DialerController {
                override fun protectFd(fd: Long): Boolean {
                    return vpnService.protect(fd.toInt())
                }
            })
            LibXray.registerListenerController(object : DialerController {
                override fun protectFd(fd: Long): Boolean {
                    return vpnService.protect(fd.toInt())
                }
            })

            val rawConfig = if (!overrideConfig.isNullOrBlank()) {
                normalizeOverrideConfig(overrideConfig, server, proxyServers)
            } else {
                generateXrayConfig(server)
            }

            val datDir = context.filesDir.resolve("xray-data").apply { mkdirs() }
            ensureXrayDatAssets(context, datDir)
            val config = XrayCoreProtocol.withAndroidRuntimeEnv(
                configJson = rawConfig,
                assetDirectory = datDir.absolutePath,
                tunFd = vpnFd
            )

            val runResult = LibXray.invoke(XrayCoreProtocol.runXrayFromJson(config))
            if (isOk(runResult)) {
                isConnected = true
                connectionError = null
                Logger.i(TAG, "Xray core started successfully")
                true
            } else {
                connectionError = extractError(runResult)
                Logger.e(TAG, "Xray start failed: ${connectionError ?: runResult}")
                disconnect()
                false
            }
        } catch (e: Exception) {
            connectionError = e.message ?: e.toString()
            Logger.e(TAG, "Xray connection error", e)
            disconnect()
            false
        }
    }

    fun disconnect() {
        runCatching { LibXray.invoke(XrayCoreProtocol.stopXray()) }
        runCatching { tunInterface?.close() }
        tunInterface = null
        isConnected = false
    }

    private fun establishTun(vpnService: VpnService): Int {
        val prefs = PreferencesManager(NebulaGuardApplication.instance)
        val useIpv6 = prefs.vpnIpType.equals("dual", ignoreCase = true)
        val tunPolicy = VpnTunPolicy.forProxyMode(prefs.proxyByApp)
        val builder = vpnService.Builder()
            .setSession("Nimbo")
            .addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .setBlocking(false)

        if (tunPolicy.publishTunnelDns) {
            builder
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
        }

        if (useIpv6) {
            builder
                .addAddress("fdfe:dcba:9876::1", 126)
                .addRoute("::", 0)
            if (tunPolicy.publishTunnelDns) {
                builder
                    .addDnsServer("2606:4700:4700::1111")
                    .addDnsServer("2001:4860:4860::8888")
            }
        }

        if (prefs.packetFragmentationEnabled) {
            builder.setMtu(1280)
        } else {
            builder.setMtu(1400)
        }

        applyUnderlyingNetworks(builder, vpnService)
        excludeSelfFromVpnWhenPossible(builder, prefs)
        applyPerAppProxyRules(builder, prefs)

        val tun = builder.establish() ?: error("Failed to establish TUN")
        tunInterface = tun
        return tun.fd
    }

    private fun applyUnderlyingNetworks(builder: VpnService.Builder, context: Context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val networks = connectivityManager.allNetworks
            .filter { network -> isUsableUnderlyingNetwork(connectivityManager, network) }
        if (networks.isEmpty()) return

        runCatching { builder.setUnderlyingNetworks(networks.toTypedArray()) }
            .onSuccess { Log.d(TAG, "VPN underlying networks set: ${networks.size}") }
            .onFailure { Log.w(TAG, "Could not set underlying networks: ${it.message}") }
    }

    private fun isUsableUnderlyingNetwork(
        connectivityManager: ConnectivityManager,
        network: Network
    ): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private fun excludeSelfFromVpnWhenPossible(
        builder: VpnService.Builder,
        prefs: PreferencesManager
    ) {
        // In VPN-only mode Android uses an allow-list; mixing it with disallowed
        // apps throws. In the default/bypass modes excluding ourselves avoids
        // control-plane HTTP and native-core sockets falling back into the tunnel.
        if (prefs.proxyByApp == 2) return
        val packageName = NebulaGuardApplication.instance.packageName
        runCatching { builder.addDisallowedApplication(packageName) }
            .onSuccess { Log.d(TAG, "Excluded self package from VPN tunnel: $packageName") }
            .onFailure { Log.w(TAG, "Could not exclude self package from VPN tunnel: ${it.message}") }
    }

    private fun applyPerAppProxyRules(
        builder: VpnService.Builder,
        prefs: PreferencesManager
    ) {
        when (prefs.proxyByApp) {
            1 -> {
                // Выбранные приложения идут в обход VPN (напрямую).
                val bypassPackages = prefs.getAppBypassList().map { it.trim() }
                    .filter { it.isNotBlank() && it != NebulaGuardApplication.instance.packageName }
                bypassPackages.forEach { packageName ->
                    runCatching { builder.addDisallowedApplication(packageName) }
                        .onFailure { Log.w(TAG, "Skip bypass package $packageName: ${it.message}") }
                }
                Log.d(TAG, "Per-app proxy mode=BYPASS_VPN, packages=${bypassPackages.size}")
            }

            2 -> {
                // Только выбранные приложения идут через VPN.
                val vpnOnlyPackages = prefs.getAppVpnOnlyList().map { it.trim() }
                    .filter { it.isNotBlank() && it != NebulaGuardApplication.instance.packageName }
                vpnOnlyPackages.forEach { packageName ->
                    runCatching { builder.addAllowedApplication(packageName) }
                        .onFailure { Log.w(TAG, "Skip VPN-only package $packageName: ${it.message}") }
                }
                Log.d(TAG, "Per-app proxy mode=VPN_ONLY, packages=${vpnOnlyPackages.size}")
            }
        }
    }

    private fun ensureXrayDatAssets(context: Context, datDir: File) {
        val marker = datDir.resolve("rules-assets.version")
        val expectedMarker = "${BuildConfig.VERSION_CODE}:${BuildConfig.VERSION_NAME}"
        val hasCurrentAssets = marker.exists() &&
            marker.readText() == expectedMarker &&
            datDir.resolve("geoip.dat").length() > 1024 &&
            datDir.resolve("geosite.dat").length() > 1024

        if (hasCurrentAssets) return

        listOf("geoip.dat", "geosite.dat").forEach { assetName ->
            runCatching {
                context.assets.open(assetName).use { input ->
                    datDir.resolve(assetName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Copied Xray rules asset: $assetName")
            }.onFailure {
                Log.w(TAG, "Xray rules asset is missing or unavailable: $assetName (${it.message})")
            }
        }

        runCatching { marker.writeText(expectedMarker) }
    }

    private fun normalizeOverrideConfig(
        config: String,
        server: Server,
        proxyServers: List<Server> = emptyList()
    ): String {
        return try {
            val json = JSONObject(config)
            val hasXrayOutbounds = json.has("outbounds") && json.optJSONArray("outbounds")?.let { arr ->
                    (0 until arr.length()).any { idx -> arr.optJSONObject(idx)?.has("protocol") == true }
                } == true
            // Если это уже клиентский Xray-конфиг - возвращаем как есть.
            // Серверные Remnawave/Xray templates с inbound-ами и только direct/block outbound-ами
            // не годятся для Android-клиента: их нужно пропустить и собрать локальный outbound.
            if (hasXrayOutbounds && RemnawaveApiClient.isUsableXrayClientConfig(json)) {
                sanitizeOverrideXrayConfig(json, server, proxyServers).toString()
            } else {
                Log.w(TAG, "Override config is not a usable Xray client JSON, fallback to generated config")
                generateXrayConfig(server)
            }
        } catch (_: Exception) {
            // Иногда может прийти share-link вместо JSON
            runCatching {
                val converted = LibXray.invoke(XrayCoreProtocol.convertShareLinksToXrayJson(config))
                if (isOk(converted)) {
                    sanitizeXrayJsonString(extractData(converted) ?: generateXrayConfig(server))
                } else {
                    generateXrayConfig(server)
                }
            }.getOrElse { generateXrayConfig(server) }
        }
    }

    private fun sanitizeXrayJsonString(config: String): String {
        return runCatching {
            val json = JSONObject(config)
            if (json.has("outbounds") || json.has("routing") || json.has("inbounds")) {
                sanitizeOverrideXrayConfig(json, null, emptyList()).toString()
            } else {
                config
            }
        }.getOrDefault(config)
    }

    private fun sanitizeOverrideXrayConfig(
        json: JSONObject,
        server: Server?,
        proxyServers: List<Server> = emptyList()
    ): JSONObject {
        val prefs = PreferencesManager(NebulaGuardApplication.instance)
        var inbounds = json.optJSONArray("inbounds") ?: JSONArray().also { json.put("inbounds", it) }
        val clientInbounds = JSONArray()
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            if (isClientSideInbound(inbound)) {
                clientInbounds.put(inbound)
            } else {
                val inboundTag = inbound.optString("tag")
                val inboundProtocol = inbound.optString("protocol")
                Log.w(
                    TAG,
                    "Dropping server-side inbound from client config: tag=$inboundTag, protocol=$inboundProtocol"
                )
            }
        }
        if (clientInbounds.length() != inbounds.length()) {
            json.put("inbounds", clientInbounds)
            inbounds = clientInbounds
        }

        var hasTunInbound = false
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            val tag = inbound.optString("tag")
            val protocol = inbound.optString("protocol")
            if (tag == "tun-in" || protocol == "tun") {
                hasTunInbound = true
                if (RoutingRuntimePolicy.shouldEnableSniffing(
                        userEnabled = prefs.trafficSniffingEnabled,
                        routingEnabled = prefs.isRoutingEnabled
                    )
                ) {
                    inbound.put("sniffing", buildSniffingConfig())
                } else {
                    inbound.remove("sniffing")
                }
            }
        }
        if (!hasTunInbound) {
            inbounds.put(buildTunInbound())
        }
        LocalProxyConfig.ensureInbound(inbounds)

        val outbounds = json.optJSONArray("outbounds") ?: JSONArray().also { json.put("outbounds", it) }
        val routing = json.optJSONObject("routing") ?: JSONObject().also { json.put("routing", it) }
        val rules = routing.optJSONArray("rules") ?: JSONArray().also { routing.put("rules", it) }

        var hasDirect = false
        var hasBlock = false
        var hasProxy = false
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.optJSONObject(i) ?: continue
            val tag = ob.optString("tag")
            if (tag.equals("direct", ignoreCase = true)) hasDirect = true
            if (tag.equals("block", ignoreCase = true)) hasBlock = true
            if (tag.equals("proxy", ignoreCase = true)) hasProxy = true
        }

        // Auto-balancer templates carry a routing.balancers entry + remnawave.injectHosts
        // but no real proxy outbounds — the bundled standard libXray does not expand
        // injectHosts, so the balancer would have nothing to balance and the connection
        // dies. Inject the profile's concrete servers as proxy/<i> outbounds (matching the
        // balancer selector prefix) and drop the remnawave key xray-core doesn't understand.
        if (proxyServers.isNotEmpty() &&
            (RemnawaveApiClient.hasBalancerOrInjectHosts(json) || (server != null && com.danila.nimbo.utils.isAutoBalancerServer(server)))
        ) {
            val injectHosts = json.optJSONObject("remnawave")?.optJSONArray("injectHosts")
            var injected = 0

            if (injectHosts != null && injectHosts.length() > 0) {
                // If there are multiple inject rules (e.g. proxy and backup), partition servers
                val hasMultiplePrefixes = injectHosts.length() > 1

                for (i in 0 until injectHosts.length()) {
                    val rule = injectHosts.optJSONObject(i) ?: continue
                    val tagPrefix = rule.optString("tagPrefix").trim()
                    val pattern = rule.optJSONObject("selector")?.optString("pattern").orEmpty().trim()

                    if (tagPrefix.isBlank()) continue

                    // Remove existing dummy/placeholder outbounds that match this tagPrefix to prevent xray from using them
                    val toRemove = mutableListOf<Int>()
                    for (k in 0 until outbounds.length()) {
                        val ob = outbounds.optJSONObject(k) ?: continue
                        val tag = ob.optString("tag").trim()
                        if (tag == tagPrefix || tag.startsWith("$tagPrefix/")) {
                            toRemove.add(k)
                        }
                    }
                    for (k in toRemove.asReversed()) {
                        outbounds.remove(k)
                    }

                    // Filter servers for this prefix:
                    val filteredServers = if (hasMultiplePrefixes) {
                        val isBackup = tagPrefix.contains("backup", ignoreCase = true) ||
                                       pattern.contains("WL", ignoreCase = true) ||
                                       pattern.contains("CDN", ignoreCase = true)
                        if (isBackup) {
                            // Аварийный пул (nimbo-fallback) тоже уходит в backup-группу,
                            // чтобы трафик переключался на него при отказе основных.
                            proxyServers.filter { com.danila.nimbo.utils.isBypassServer(it) || it.isFallback }
                        } else {
                            proxyServers.filter { !com.danila.nimbo.utils.isBypassServer(it) && !it.isFallback }
                        }
                    } else {
                        // Single prefix: put all servers
                        proxyServers
                    }

                    filteredServers.forEachIndexed { index, proxyServer ->
                        runCatching { buildProxyOutbound(proxyServer, "$tagPrefix/$index") }
                            .onSuccess { outbounds.put(it); injected++ }
                            .onFailure { Log.w(TAG, "Skip balancer proxy ${proxyServer.name}: ${it.message}") }
                    }
                }
            } else {
                // Fallback to old single-prefix selector logic if injectHosts is missing
                val tagPrefix = resolveBalancerProxyTagPrefix(routing, json)

                // Remove existing dummy/placeholder outbounds that match this tagPrefix to prevent xray from using them
                val toRemove = mutableListOf<Int>()
                for (k in 0 until outbounds.length()) {
                    val ob = outbounds.optJSONObject(k) ?: continue
                    val tag = ob.optString("tag").trim()
                    if (tag == tagPrefix || tag.startsWith("$tagPrefix/")) {
                        toRemove.add(k)
                    }
                }
                for (k in toRemove.asReversed()) {
                    outbounds.remove(k)
                }

                proxyServers.forEachIndexed { index, proxyServer ->
                    runCatching { buildProxyOutbound(proxyServer, "$tagPrefix/$index") }
                        .onSuccess { outbounds.put(it); injected++ }
                        .onFailure { Log.w(TAG, "Skip balancer proxy ${proxyServer.name}: ${it.message}") }
                }
            }

            if (injected > 0) {
                hasProxy = true
                json.remove("remnawave")
                Log.i(TAG, "Injected $injected balancer proxy outbounds successfully")
            }
        }

        var injectedFallbackProxy = false
        val fallbackServer = server
        if (!hasProxy && fallbackServer != null && shouldInjectFallbackProxyForOverride(json, routing, rules, fallbackServer)) {
            val protocol = canonicalXrayOutboundProtocol(fallbackServer.protocol)
            outbounds.put(
                JSONObject().apply {
                    put("tag", "proxy")
                    put("protocol", protocol)
                    put("settings", buildOutboundSettings(fallbackServer, protocol))
                    buildStreamSettings(fallbackServer)?.let { put("streamSettings", it) }
                    applyMuxSettings(this, fallbackServer, protocol)
                }
            )
            hasProxy = true
            injectedFallbackProxy = true
        }
        if (!hasDirect) {
            outbounds.put(
                JSONObject()
                    .put("tag", "direct")
                    .put("protocol", "freedom")
                    .put("settings", JSONObject().put("domainStrategy", "UseIP"))
            )
        }
        if (!hasBlock) {
            outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        }
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (!rule.has("type")) {
                rule.put("type", "field")
            }
            if (!rule.has("outboundTag") && rule.has("outTag")) {
                rule.put("outboundTag", rule.optString("outTag"))
            }
            includeTunInboundForProxyStyleRule(rule)
        }
        val normalizedRules = reorderRoutingRules(rules)
        val selectedOutboundTag = resolveSelectedRemoteOutboundTag(outbounds, server)
        val balancerTag = resolvePreferredBalancerTag(routing, normalizedRules, server)
        val finalRules = JSONArray()
        if (!selectedOutboundTag.isNullOrBlank() && !hasTunInboundOutboundRule(normalizedRules, selectedOutboundTag)) {
            finalRules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun-in"))
                    .put("outboundTag", selectedOutboundTag)
            )
        } else if (!balancerTag.isNullOrBlank() && !hasTunInboundBalancerRule(normalizedRules, balancerTag)) {
            finalRules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun-in"))
                    .put("balancerTag", balancerTag)
            )
        }
        for (i in 0 until normalizedRules.length()) {
            finalRules.put(normalizedRules.optJSONObject(i))
        }
        // В шаблонных конфигурациях нельзя насильно добавлять catch-all в proxy:
        // это перетирает DIRECT/BLOCK/балансировочные правила из панели.
        if (injectedFallbackProxy && hasProxy && !hasProxyCatchAllRule(finalRules)) {
            finalRules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun-in"))
                    .put("outboundTag", "proxy")
            )
        }
        val probeOutboundTag = selectedOutboundTag
            ?: if (balancerTag.isNullOrBlank()) LocalProxyConfig.firstProxyOutboundTag(outbounds) else null
        val routedRules = LocalProxyConfig.prependRoute(
            rules = finalRules,
            outboundTag = probeOutboundTag,
            balancerTag = if (probeOutboundTag.isNullOrBlank()) balancerTag else null
        )
        routing.put("rules", routedRules)

        applyGeneratedNetworkPreferences(json)
        // Routing profiles prepend their own rules. Re-assert the tagged health route
        // afterwards so no catch-all/direct rule can make a probe bypass the candidate.
        routing.put(
            "rules",
            LocalProxyConfig.prependRoute(
                rules = routing.optJSONArray("rules") ?: JSONArray(),
                outboundTag = probeOutboundTag,
                balancerTag = if (probeOutboundTag.isNullOrBlank()) balancerTag else null
            )
        )
        applyTlsFragment(json)
        applyConnectionPolicy(json)

        return json
    }

    // TLS Fragment (opt-in DPI bypass). Chains real proxy outbounds through an xray "fragment"
    // freedom dialer that splits the TLS ClientHello so the SNI is not sent as one segment.
    // Off by default; only applied when the user enables it in settings.
    private fun applyTlsFragment(json: JSONObject) {
        val enabled = runCatching {
            PreferencesManager(NebulaGuardApplication.instance).tlsFragment
        }.getOrDefault(false)
        if (!enabled) return
        val outbounds = json.optJSONArray("outbounds") ?: return

        var hasFragment = false
        for (i in 0 until outbounds.length()) {
            if (outbounds.optJSONObject(i)?.optString("tag").equals("fragment", ignoreCase = true)) {
                hasFragment = true
                break
            }
        }
        if (!hasFragment) {
            outbounds.put(
                JSONObject()
                    .put("tag", "fragment")
                    .put("protocol", "freedom")
                    .put(
                        "settings",
                        JSONObject().put(
                            "fragment",
                            JSONObject()
                                .put("packets", "tlshello")
                                .put("length", "100-200")
                                .put("interval", "10-20")
                        )
                    )
                    .put(
                        "streamSettings",
                        JSONObject().put("sockopt", JSONObject().put("tcpNoDelay", true))
                    )
            )
        }

        val skipTags = setOf("direct", "block", "fragment", "dns")
        val skipProtocols = setOf("freedom", "blackhole", "dns", "loopback")
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.optJSONObject(i) ?: continue
            val tag = ob.optString("tag").trim().lowercase()
            val protocol = ob.optString("protocol").trim().lowercase()
            if (tag in skipTags || protocol in skipProtocols) continue
            val stream = ob.optJSONObject("streamSettings")
                ?: JSONObject().also { ob.put("streamSettings", it) }
            val sockopt = stream.optJSONObject("sockopt")
                ?: JSONObject().also { stream.put("sockopt", it) }
            if (!sockopt.has("dialerProxy")) {
                sockopt.put("dialerProxy", "fragment")
            }
        }
    }

    private fun isClientSideInbound(inbound: JSONObject): Boolean {
        return when (inbound.optString("protocol").trim().lowercase()) {
            "tun", "socks", "http", "dokodemo-door" -> true
            else -> false
        }
    }

    private fun shouldInjectFallbackProxyForOverride(
        json: JSONObject,
        routing: JSONObject,
        rules: JSONArray,
        server: Server?
    ): Boolean {
        if (server == null) return false
        if (server.uuid == "remote" || server.host.equals("API", ignoreCase = true)) return false

        // Для панельных template/balancer-конфигов используем их outbounds/routing как есть.
        if (json.has("observatory")) return false
        if ((routing.optJSONArray("balancers")?.length() ?: 0) > 0) return false
        if ((rules.length()) > 0) return false

        return true
    }

    private fun resolveSelectedRemoteOutboundTag(outbounds: JSONArray, server: Server?): String? {
        val selectedTag = server?.remoteOutboundTag?.trim()?.takeIf { it.isNotBlank() } ?: return null
        for (i in 0 until outbounds.length()) {
            if (outbounds.optJSONObject(i)?.optString("tag")?.equals(selectedTag, ignoreCase = true) == true) {
                return selectedTag
            }
        }
        Log.w(TAG, "Selected remote outbound '$selectedTag' is absent from config; use template default")
        return null
    }

    private fun resolvePreferredBalancerTag(routing: JSONObject, rules: JSONArray, server: Server?): String? {
        val selectedTag = server?.remoteBalancerTag?.trim()?.takeIf { it.isNotBlank() }
        if (selectedTag != null) {
            val balancers = routing.optJSONArray("balancers")
            for (i in 0 until (balancers?.length() ?: 0)) {
                val tag = balancers?.optJSONObject(i)?.optString("tag")?.trim().orEmpty()
                if (tag.equals(selectedTag, ignoreCase = true)) return tag
            }
            Log.w(TAG, "Selected Remnawave balancer '$selectedTag' is absent from config; use template default")
        }
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val balancerTag = rule.optString("balancerTag").trim()
            if (balancerTag.isNotBlank()) return balancerTag
        }

        val balancers = routing.optJSONArray("balancers") ?: return null
        for (i in 0 until balancers.length()) {
            val balancer = balancers.optJSONObject(i) ?: continue
            val tag = balancer.optString("tag").trim()
            if (tag.isNotBlank()) return tag
        }
        return null
    }

    private fun hasTunInboundOutboundRule(rules: JSONArray, outboundTag: String): Boolean {
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val tag = rule.optString("outboundTag", rule.optString("outTag"))
            if (!tag.equals(outboundTag, ignoreCase = true)) continue
            when (val inboundTag = rule.opt("inboundTag")) {
                is JSONArray -> {
                    for (j in 0 until inboundTag.length()) {
                        if (inboundTag.optString(j).equals("tun-in", ignoreCase = true)) return true
                    }
                }
                is String -> if (inboundTag.equals("tun-in", ignoreCase = true)) return true
                else -> if (!rule.has("inboundTag")) return true
            }
        }
        return false
    }

    private fun hasTunInboundBalancerRule(rules: JSONArray, balancerTag: String): Boolean {
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (!rule.optString("balancerTag").equals(balancerTag, ignoreCase = true)) continue
            when (val inboundTag = rule.opt("inboundTag")) {
                is JSONArray -> {
                    for (j in 0 until inboundTag.length()) {
                        if (inboundTag.optString(j).equals("tun-in", ignoreCase = true)) return true
                    }
                }
                is String -> {
                    if (inboundTag.equals("tun-in", ignoreCase = true)) return true
                }
                else -> {
                    // Правило без inboundTag действует глобально, включая tun-in.
                    if (!rule.has("inboundTag")) return true
                }
            }
        }
        return false
    }

    private fun hasProxyCatchAllRule(rules: JSONArray): Boolean {
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (isProxyCatchAllRule(rule)) return true
        }
        return false
    }

    private fun reorderRoutingRules(rules: JSONArray): JSONArray {
        val specificRules = mutableListOf<JSONObject>()
        val proxyCatchAllRules = mutableListOf<JSONObject>()

        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (isProxyCatchAllRule(rule)) {
                proxyCatchAllRules += rule
            } else {
                specificRules += rule
            }
        }

        return JSONArray().apply {
            specificRules.forEach { put(it) }
            proxyCatchAllRules.forEach { put(it) }
        }
    }

    private fun isProxyCatchAllRule(rule: JSONObject): Boolean {
        val outboundTag = rule.optString("outboundTag", rule.optString("outTag"))
        if (!outboundTag.equals("proxy", ignoreCase = true)) return false

        val conditionalKeys = listOf("domain", "ip", "port", "protocol", "source", "sourcePort", "user")
        val hasSpecificCondition = conditionalKeys.any { key ->
            when (val value = rule.opt(key)) {
                is JSONArray -> value.length() > 0
                is String -> value.isNotBlank()
                null -> false
                else -> true
            }
        }
        if (hasSpecificCondition) return false

        val network = rule.optString("network").trim().lowercase()
        val networkIsCatchAll = network.isBlank() ||
            network == "tcp,udp" ||
            network == "udp,tcp" ||
            network == "tcp, udp" ||
            network == "udp, tcp"

        return networkIsCatchAll
    }

    private fun includeTunInboundForProxyStyleRule(rule: JSONObject) {
        when (val inboundTag = rule.opt("inboundTag")) {
            is JSONArray -> {
                var hasTun = false
                var targetsLocalProxyInbound = false
                for (i in 0 until inboundTag.length()) {
                    val tag = inboundTag.optString(i)
                    if (tag == "tun-in") hasTun = true
                    if (tag == "socks" || tag == "http") targetsLocalProxyInbound = true
                }
                if (targetsLocalProxyInbound && !hasTun) {
                    inboundTag.put("tun-in")
                }
            }
            is String -> {
                if (inboundTag == "socks" || inboundTag == "http") {
                    rule.put("inboundTag", JSONArray().put(inboundTag).put("tun-in"))
                }
            }
        }
    }

    private fun buildSniffingConfig(): JSONObject {
        return JSONObject()
            .put("enabled", true)
            .put("routeOnly", false)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
    }

    private fun buildTunInbound(): JSONObject {
        val prefs = PreferencesManager(NebulaGuardApplication.instance)
        return JSONObject().apply {
            put("tag", "tun-in")
            put("protocol", "tun")
            put("settings", JSONObject().apply {
                put("name", "tun0")
                put("MTU", if (prefs.packetFragmentationEnabled) 1280 else 1400)
            })
            if (RoutingRuntimePolicy.shouldEnableSniffing(
                    userEnabled = prefs.trafficSniffingEnabled,
                    routingEnabled = prefs.isRoutingEnabled
                )
            ) {
                put("sniffing", buildSniffingConfig())
            }
        }
    }

    private fun buildProxyOutbound(server: Server, tag: String): JSONObject {
        val protocol = canonicalXrayOutboundProtocol(server.protocol)
        return JSONObject().apply {
            put("tag", tag)
            put("protocol", protocol)
            put("settings", buildOutboundSettings(server, protocol))
            buildStreamSettings(server)?.let { put("streamSettings", it) }
            applyMuxSettings(this, server, protocol)
        }
    }

    private val nonProxyOutboundProtocols = setOf("freedom", "blackhole", "dns", "loopback")

    private fun hasUsableProxyOutbound(outbounds: JSONArray): Boolean {
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.optJSONObject(i) ?: continue
            val protocol = ob.optString("protocol").trim().lowercase()
            if (protocol.isNotBlank() && protocol !in nonProxyOutboundProtocols) return true
        }
        return false
    }

    /**
     * The injected proxy tags must match the balancer's selector prefix so leastLoad /
     * burstObservatory pick them up. Prefer the balancer selector, then the
     * remnawave.injectHosts tagPrefix, and finally the conventional "proxy".
     */
    private fun resolveBalancerProxyTagPrefix(routing: JSONObject, json: JSONObject): String {
        routing.optJSONArray("balancers")?.let { balancers ->
            for (i in 0 until balancers.length()) {
                val b = balancers.optJSONObject(i) ?: continue
                val selectorOpt = b.opt("selector")
                if (selectorOpt is JSONArray) {
                    val first = selectorOpt.optString(0).trim()
                    if (first.isNotBlank()) return first
                } else if (selectorOpt is String) {
                    val trimmed = selectorOpt.trim()
                    if (trimmed.isNotBlank()) return trimmed
                }
            }
        }
        json.optJSONObject("remnawave")?.optJSONArray("injectHosts")?.let { inject ->
            for (i in 0 until inject.length()) {
                val prefix = inject.optJSONObject(i)?.optString("tagPrefix")?.trim().orEmpty()
                if (prefix.isNotBlank()) return prefix
            }
        }
        return "proxy"
    }

    private fun generateXrayConfig(server: Server): String {
        val prefs = PreferencesManager(NebulaGuardApplication.instance)
        val routingProfile = activeRoutingProfile(prefs)
        val protocol = canonicalXrayOutboundProtocol(server.protocol)

        val outbound = JSONObject().apply {
            put("tag", "proxy")
            put("protocol", protocol)
            put("settings", buildOutboundSettings(server, protocol))
            buildStreamSettings(server)?.let { put("streamSettings", it) }
            applyMuxSettings(this, server, protocol)
        }

        val root = JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("dns", JSONObject().put("servers", buildGeneratedDnsServers(routingProfile)))
            put(
                "inbounds",
                JSONArray().put(buildTunInbound()).also(LocalProxyConfig::ensureInbound)
            )
            put("outbounds", JSONArray().apply {
                put(outbound)
                put(
                    JSONObject()
                        .put("tag", "direct")
                        .put("protocol", "freedom")
                        .put("settings", JSONObject().put("domainStrategy", "UseIP"))
                )
                put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            })
            put("routing", JSONObject().apply {
                put("domainStrategy", routingProfile?.domainStrategy?.takeIf { it.isNotBlank() } ?: "IPIfNonMatch")
                put(
                    "rules",
                    LocalProxyConfig.prependRoute(
                        rules = buildGeneratedRoutingRules(routingProfile),
                        outboundTag = "proxy",
                        balancerTag = null
                    )
                )
            })
        }

        applyConnectionPolicy(root)
        return root.toString()
    }

    private fun applyMuxSettings(outbound: JSONObject, server: Server, protocol: String) {
        val prefs = PreferencesManager(NebulaGuardApplication.instance)
        if (server.flow?.isNotBlank() == true && protocol == "vless") {
            outbound.put("mux", JSONObject().put("enabled", false))
        } else if (prefs.muxEnabled) {
            outbound.put(
                "mux",
                JSONObject()
                    .put("enabled", true)
                    .put("concurrency", 8)
            )
        }
    }

    private fun applyGeneratedNetworkPreferences(json: JSONObject) {
        val prefs = PreferencesManager(NebulaGuardApplication.instance)
        val routingProfile = activeRoutingProfile(prefs)
        val routing = json.optJSONObject("routing") ?: JSONObject().also { json.put("routing", it) }
        val rules = routing.optJSONArray("rules") ?: JSONArray()
        val updated = JSONArray()

        routingProfile?.let { profile ->
            routing.put("domainStrategy", profile.domainStrategy?.takeIf { it.isNotBlank() } ?: "IPIfNonMatch")
            val profileRules = RoutingProfileRules.build(profile, includeFallback = false)
            for (i in 0 until profileRules.length()) {
                updated.put(profileRules.getJSONObject(i))
            }
        }

        if (prefs.blockUdp) {
            updated.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun-in"))
                    .put("network", "udp")
                    .put("outboundTag", "block")
            )
        }

        for (i in 0 until rules.length()) {
            updated.put(rules.optJSONObject(i))
        }
        routing.put("rules", updated)
    }

    private fun applyConnectionPolicy(json: JSONObject) {
        val prefs = PreferencesManager(NebulaGuardApplication.instance)
        val policy = json.optJSONObject("policy") ?: JSONObject().also { json.put("policy", it) }
        val levels = policy.optJSONObject("levels") ?: JSONObject().also { policy.put("levels", it) }
        val defaultLevel = levels.optJSONObject("0") ?: JSONObject().also { levels.put("0", it) }
        defaultLevel.put("connIdle", prefs.idleTimeoutSeconds)
    }

    private fun buildGeneratedRoutingRules(routingProfile: RoutingProfile?): JSONArray {
        val prefs = PreferencesManager(NebulaGuardApplication.instance)
        val dnsMode = prefs.vpnDnsMode.lowercase()
        return JSONArray().apply {
            if (dnsMode == "local" || dnsMode == "hybrid") {
                put(
                    JSONObject().apply {
                        put("type", "field")
                        put("inboundTag", JSONArray().put("tun-in"))
                        put("network", "udp,tcp")
                        put("port", "53,853")
                        put("outboundTag", "direct")
                    }
                )
            }
            if (prefs.blockUdp) {
                put(
                    JSONObject().apply {
                        put("type", "field")
                        put("inboundTag", JSONArray().put("tun-in"))
                        put("network", "udp")
                        put("outboundTag", "block")
                    }
                )
            }
            put(
                JSONObject().apply {
                    put("type", "field")
                    put("inboundTag", JSONArray().put("tun-in"))
                    put("protocol", JSONArray().put("bittorrent"))
                    put("outboundTag", "direct")
                }
            )
            if (routingProfile == null && prefs.allowLanConnections && !prefs.lanThroughProxy) {
                put(
                    JSONObject().apply {
                        put("type", "field")
                        put("inboundTag", JSONArray().put("tun-in"))
                        put("ip", JSONArray().put("geoip:private"))
                        put("outboundTag", "direct")
                    }
                )
            }
            if (routingProfile != null) {
                val profileRules = RoutingProfileRules.build(routingProfile, includeFallback = true)
                for (i in 0 until profileRules.length()) put(profileRules.getJSONObject(i))
            } else {
                put(
                    JSONObject().apply {
                        put("type", "field")
                        put("inboundTag", JSONArray().put("tun-in"))
                        put("ip", JSONArray().put("geoip:ru"))
                        put("outboundTag", "direct")
                    }
                )
                put(
                    JSONObject().apply {
                        put("type", "field")
                        put("inboundTag", JSONArray().put("tun-in"))
                        put(
                            "domain",
                            JSONArray()
                                .put("2ip.io")
                                .put("2ip.ru")
                                .put("regexp:.*\\.ru$")
                                .put("regexp:.*\\.xn--p1ai$")
                                .put("regexp:.*\\.su$")
                        )
                        put("outboundTag", "direct")
                    }
                )
                put(
                    JSONObject().apply {
                        put("type", "field")
                        put("inboundTag", JSONArray().put("tun-in"))
                        put("outboundTag", "proxy")
                    }
                )
            }
        }
    }

    private fun activeRoutingProfile(prefs: PreferencesManager): RoutingProfile? =
        prefs.loadRoutingProfile().takeIf { prefs.isRoutingEnabled }

    private fun buildGeneratedDnsServers(profile: RoutingProfile?): JSONArray {
        val servers = linkedSetOf<String>()
        listOf(
            profile?.remoteDNSDomain,
            profile?.remoteDNSIP,
            profile?.domesticDNSDomain,
            profile?.domesticDNSIP
        ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .forEach(servers::add)
        if (servers.isEmpty()) servers += listOf("1.1.1.1", "8.8.8.8")
        return JSONArray(servers.toList())
    }

    private fun canonicalXrayOutboundProtocol(protocolRaw: String): String {
        val protocol = protocolRaw.trim().lowercase()
        return when {
            protocol.contains("vless") -> "vless"
            protocol.contains("vmess") -> "vmess"
            protocol.contains("trojan") -> "trojan"
            protocol == "ss" || protocol.contains("shadowsocks") -> "shadowsocks"
            protocol == "hy2" || protocol.contains("hysteria") -> "hysteria"
            else -> protocol
        }
    }

    private fun buildOutboundSettings(server: Server, protocol: String): JSONObject {
        return when (protocol) {
            "vless" -> JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", server.host).put("port", server.port).put("users", JSONArray().put(
                    JSONObject().put("id", server.uuid).put("encryption", "none").apply {
                        server.flow?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
                    }
                ))
            ))
            "vmess" -> JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", server.host).put("port", server.port).put("users", JSONArray().put(
                    JSONObject().put("id", server.uuid).put("alterId", server.alterId ?: 0).put("security", server.security ?: "auto")
                ))
            ))
            "trojan" -> JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", server.host).put("port", server.port).put("password", server.uuid)
            ))
            "ss", "shadowsocks" -> JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", server.host).put("port", server.port)
                    .put("method", server.method ?: "chacha20-poly1305")
                    .put("password", server.uuid)
            ))
            "hysteria" -> JSONObject()
                .put("version", 2)
                .put("address", server.host)
                .put("port", server.port)
            else -> error("Unsupported protocol for Xray: $protocol")
        }
    }

    private fun buildStreamSettings(server: Server): JSONObject? {
        val serverProtocol = server.protocol.trim().lowercase()
        val rawNetwork = server.network
            ?.lowercase()
            ?.ifBlank { null }
            ?: if (serverProtocol == "hy2" || serverProtocol.contains("hysteria")) "hysteria" else "tcp"
        val network = when (rawNetwork) {
            "xhttp", "splithttp" -> "xhttp"
            "h2", "http2" -> "h2"
            "httpupgrade", "http-upgrade" -> "httpupgrade"
            "hy2", "hysteria2" -> "hysteria"
            else -> rawNetwork
        }
        val security = when {
            server.protocol.equals("hysteria", ignoreCase = true) ||
                server.protocol.equals("hy2", ignoreCase = true) ||
                server.protocol.equals("hysteria2", ignoreCase = true) -> "tls"
            server.security.equals("reality", ignoreCase = true) -> "reality"
            server.protocol.contains("trojan", ignoreCase = true) &&
                !server.security.equals("none", ignoreCase = true) -> "tls"
            server.tls == true || server.security.equals("tls", ignoreCase = true) -> "tls"
            else -> "none"
        }

        val stream = JSONObject().put("network", network).put("security", security)

        when (network) {
            "ws" -> stream.put("wsSettings", JSONObject().apply {
                put("path", server.path ?: "/")
                server.hostHeader?.takeIf { it.isNotBlank() }?.let {
                    put("headers", JSONObject().put("Host", it))
                }
            })
            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", server.serviceName ?: "grpc"))
            "xhttp" -> stream.put("xhttpSettings", JSONObject().apply {
                put("path", server.path ?: "/")
                put("mode", "auto")
                server.hostHeader?.takeIf { it.isNotBlank() }?.let {
                    put("host", it)
                }
            })
            "h2" -> stream.put("httpSettings", JSONObject().apply {
                put("path", server.path ?: "/")
                server.hostHeader?.takeIf { it.isNotBlank() }?.let {
                    put("host", JSONArray().put(it))
                }
            })
            "httpupgrade" -> stream.put("httpupgradeSettings", JSONObject().apply {
                put("path", server.path ?: "/")
                server.hostHeader?.takeIf { it.isNotBlank() }?.let {
                    put("host", it)
                }
            })
            "hysteria" -> {
                stream.put("hysteriaSettings", JSONObject().apply {
                    put("version", 2)
                    server.uuid.takeIf { it.isNotBlank() }?.let { put("auth", it) }
                    put("udpIdleTimeout", 60)
                })
                buildHysteriaFinalMask(server)?.let { stream.put("finalmask", it) }
            }
            "tcp" -> {
                // no extra settings
            }
        }

        if (security == "tls") {
            stream.put("tlsSettings", JSONObject().apply {
                put("serverName", server.sni ?: server.host)
                put("allowInsecure", server.allowInsecure ?: false)
                val alpnValue = server.alpn?.takeIf { it.isNotBlank() } ?: if (network == "hysteria") "h3" else null
                alpnValue?.let { alpn ->
                    put("alpn", JSONArray().apply { alpn.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { put(it) } })
                }
                server.fingerprint?.takeIf { it.isNotBlank() }?.let { put("fingerprint", it) }
            })
        }

        if (security == "reality") {
            stream.put("realitySettings", JSONObject().apply {
                put("serverName", server.sni ?: server.host)
                server.publicKey?.takeIf { it.isNotBlank() }?.let { put("publicKey", it) }
                server.shortId?.takeIf { it.isNotBlank() }?.let { put("shortId", it) }
                server.spiderX?.takeIf { it.isNotBlank() }?.let { put("spiderX", it) }
                put("fingerprint", server.fingerprint ?: "chrome")
            })
        }

        return stream
    }

    private fun buildHysteriaFinalMask(server: Server): JSONObject? {
        val finalMask = JSONObject()
        val udpLayers = JSONArray()

        val obfs = server.hysteriaObfs?.trim()?.lowercase().orEmpty()
        val obfsPassword = server.hysteriaObfsPassword?.trim().orEmpty()
        if (obfs == "salamander" && obfsPassword.isNotBlank()) {
            udpLayers.put(
                JSONObject()
                    .put("type", "salamander")
                    .put("settings", JSONObject().put("password", obfsPassword))
            )
        }
        if (udpLayers.length() > 0) {
            finalMask.put("udp", udpLayers)
        }

        val quicParams = JSONObject()
        server.hysteriaCongestion?.trim()?.takeIf { it.isNotBlank() }?.let {
            quicParams.put("congestion", it)
        }
        server.hysteriaUp?.trim()?.takeIf { it.isNotBlank() }?.let {
            quicParams.put("brutalUp", normalizeHysteriaBandwidth(it))
        }
        server.hysteriaDown?.trim()?.takeIf { it.isNotBlank() }?.let {
            quicParams.put("brutalDown", normalizeHysteriaBandwidth(it))
        }
        server.hysteriaPorts?.trim()?.takeIf { it.isNotBlank() }?.let { ports ->
            quicParams.put(
                "udpHop",
                JSONObject()
                    .put("ports", ports)
                    .put("interval", server.hysteriaHopInterval?.trim()?.takeIf { it.isNotBlank() } ?: "30")
            )
        }
        if (quicParams.length() > 0) {
            finalMask.put("quicParams", quicParams)
        }

        return finalMask.takeIf { it.length() > 0 }
    }

    private fun normalizeHysteriaBandwidth(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return value
        return if (value.all { it.isDigit() }) "$value mbps" else value
    }

    private fun isOk(result: String?): Boolean {
        if (result.isNullOrBlank()) return false
        val decoded = decodeIfBase64(result)
        return try {
            val json = JSONObject(decoded)
            json.optBoolean("ok", false) ||
                json.optBoolean("success", false) ||
                json.optInt("code", -1) == 0
        } catch (_: Exception) {
            decoded.equals("ok", ignoreCase = true)
        }
    }

    private fun extractError(result: String?): String {
        if (result.isNullOrBlank()) return "Unknown Xray error"
        val decoded = decodeIfBase64(result)
        return try {
            val json = JSONObject(decoded)
            json.optString("error").ifBlank { decoded }
        } catch (_: Exception) {
            decoded
        }
    }

    private fun extractData(result: String?): String? {
        if (result.isNullOrBlank()) return null
        val decoded = decodeIfBase64(result)
        return runCatching { JSONObject(decoded).optString("data").takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun decodeIfBase64(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
        return runCatching {
            String(Base64.getDecoder().decode(trimmed), Charsets.UTF_8)
        }.getOrDefault(trimmed)
    }
}
