package com.danila.nimbo.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.utils.PreferencesManager

/**
 * Единая точка создания TUN-интерфейса. Используется обоими движками:
 * Xray (XrayManager.establishTun) и AmneziaWG (AmneziaWgManager).
 * Параметры интерфейса завязаны на настройки пользователя (IP-версия,
 * DNS, MTU, per-app режимы), поэтому здесь дублируется прежняя логика
 * XrayManager и остаётся идентичной для обоих ядер.
 */
object VpnInterfaceBuilder {

    private const val TAG = "VpnInterfaceBuilder"

    fun establish(vpnService: VpnService, underlyingNetwork: Network?): ParcelFileDescriptor {
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

        applyUnderlyingNetwork(builder, underlyingNetwork)
        excludeSelfFromVpnWhenPossible(builder, prefs)
        applyPerAppProxyRules(builder, prefs)

        val descriptor = try {
            builder.establish()
        } catch (e: IllegalArgumentException) {
            // Кидается на кривых адресах/маршрутах и на несуществующем пакете
            // в per-app списке — раньше это выглядело как «не удалось запустить».
            throw IllegalStateException(
                "${ConnectionFailureClassifier.TUN_MARKER}: система отклонила параметры интерфейса (${e.message})",
                e
            )
        } catch (e: IllegalStateException) {
            throw IllegalStateException(
                "${ConnectionFailureClassifier.TUN_MARKER}: ${e.message ?: "система отклонила запрос"}",
                e
            )
        }

        return descriptor ?: error(
            "${ConnectionFailureClassifier.TUN_MARKER}: ${describeNullDescriptor(vpnService, prefs)}"
        )
    }

    /**
     * `establish()` возвращает null молча — причину приходится выяснять отдельно.
     * Это и есть «точная причина вместо “не удалось запустить”»: разрешение не
     * выдано, его отозвали, туннель забрало другое приложение или список
     * per-app правил оказался пустым в режиме «только выбранные».
     */
    private fun describeNullDescriptor(
        vpnService: VpnService,
        prefs: PreferencesManager
    ): String {
        val consentMissing = runCatching {
            VpnService.prepare(vpnService.applicationContext) != null
        }.getOrDefault(false)
        if (consentMissing) {
            return "разрешение на VPN не выдано или отозвано системой"
        }

        if (prefs.proxyByApp == 2 && prefs.getAppVpnOnlyList().none { it.isNotBlank() }) {
            return "включён режим «только выбранные приложения», но список пуст"
        }

        val foreignVpn = runCatching { hasForeignVpnTransport(vpnService) }.getOrDefault(false)
        if (foreignVpn) {
            return "туннель удерживает другое VPN-приложение"
        }

        return "система вернула пустой дескриптор без объяснения"
    }

    /** Есть ли активный VPN-транспорт, который поднял не Nimbo. */
    private fun hasForeignVpnTransport(vpnService: VpnService): Boolean {
        val manager = vpnService.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    /**
     * Защищает сокет движка от замыкания в собственный VPN и при наличии
     * базовой сети привязывает его к ней.
     */
    fun protectSocket(
        vpnService: VpnService,
        underlyingNetwork: Network?,
        fd: Int
    ): Boolean {
        if (!vpnService.protect(fd)) {
            Log.w(TAG, "Could not protect outbound socket from the VPN")
            return false
        }
        if (underlyingNetwork == null) return true

        return runCatching {
            ParcelFileDescriptor.fromFd(fd).use { duplicate ->
                underlyingNetwork.bindSocket(duplicate.fileDescriptor)
            }
            true
        }.onFailure { error ->
            Log.w(
                TAG,
                "Could not bind outbound socket to network ${underlyingNetwork.networkHandle}: ${error.message}"
            )
        }.getOrDefault(false)
    }

    private fun applyUnderlyingNetwork(builder: VpnService.Builder, network: Network?) {
        if (network == null) return
        runCatching { builder.setUnderlyingNetworks(arrayOf(network)) }
            .onSuccess { Log.d(TAG, "VPN underlying network set: ${network.networkHandle}") }
            .onFailure { Log.w(TAG, "Could not set underlying network: ${it.message}") }
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
}
