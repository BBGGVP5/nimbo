package com.danila.nimbo.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTunPolicyTest {

    @Test
    fun vpnOnly_doesNotPublishTunnelDnsToUnselectedApps() {
        assertFalse(VpnTunPolicy.forProxyMode(2).publishTunnelDns)
    }

    @Test
    fun defaultAndBypassModes_publishTunnelDns() {
        assertTrue(VpnTunPolicy.forProxyMode(0).publishTunnelDns)
        assertTrue(VpnTunPolicy.forProxyMode(1).publishTunnelDns)
    }

    @Test
    fun vpnOnly_requiresAtLeastOneInstalledSelectedApplication() {
        val installedPackages = setOf("org.telegram.messenger")

        assertFalse(
            VpnTunPolicy.hasUsableVpnOnlySelection(
                proxyByApp = VpnTunPolicy.VPN_ONLY_MODE,
                ownPackage = "com.danila.nimbo",
                selectedPackages = emptySet(),
                isInstalled = installedPackages::contains
            )
        )
        assertTrue(
            VpnTunPolicy.hasUsableVpnOnlySelection(
                proxyByApp = VpnTunPolicy.VPN_ONLY_MODE,
                ownPackage = "com.danila.nimbo",
                selectedPackages = setOf("org.telegram.messenger"),
                isInstalled = installedPackages::contains
            )
        )
    }

    @Test
    fun vpnOnly_ignoresOwnBlankAndMissingPackages() {
        assertFalse(
            VpnTunPolicy.hasUsableVpnOnlySelection(
                proxyByApp = VpnTunPolicy.VPN_ONLY_MODE,
                ownPackage = "com.danila.nimbo",
                selectedPackages = setOf(" ", "com.danila.nimbo", "missing.package"),
                isInstalled = { false }
            )
        )
    }

    @Test
    fun nonVpnOnlyModes_doNotRequireApplicationSelection() {
        assertTrue(
            VpnTunPolicy.hasUsableVpnOnlySelection(
                proxyByApp = 0,
                ownPackage = "com.danila.nimbo",
                selectedPackages = emptySet(),
                isInstalled = { false }
            )
        )
    }
}
