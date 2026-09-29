package com.danila.nimbo.vpn

/**
 * DNS servers published by a VPN are process-wide on some Android versions.
 * In VPN-only mode this would make unselected applications resolve names
 * through a tunnel they are not allowed to use, so keep their DNS direct.
 */
data class VpnTunPolicy(
    val publishTunnelDns: Boolean
) {
    companion object {
        fun forProxyMode(proxyByApp: Int): VpnTunPolicy = VpnTunPolicy(
            publishTunnelDns = proxyByApp != VPN_ONLY_MODE
        )

        fun hasUsableVpnOnlySelection(
            proxyByApp: Int,
            ownPackage: String,
            selectedPackages: Set<String>,
            isInstalled: (String) -> Boolean
        ): Boolean {
            if (proxyByApp != VPN_ONLY_MODE) return true

            return selectedPackages
                .asSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && it != ownPackage }
                .any(isInstalled)
        }

        const val VPN_ONLY_MODE = 2
    }
}
