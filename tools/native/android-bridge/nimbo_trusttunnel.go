//go:build android

package libXray

import trusttunnel "nimbo/trusttunnelcore"

// TrustTunnel runs in the same Go runtime as LibXray: Mihomo's protocol client
// behind an authenticated loopback SOCKS5 that Xray's socks outbound uses.
// The endpoint sockets are protected through the VpnService controller so
// they never loop back into the device tunnel.

func trustTunnelProtector(controller DialerController) trusttunnel.Protector {
	if controller == nil {
		// Only for probes while no VPN is up: the app is excluded from its own
		// tunnel, so unprotected sockets still use the physical network.
		return nil
	}
	return func(fd uintptr) bool { return controller.ProtectFd(int(fd)) }
}

// NimboTrustTunnelStart replaces any running instance and verifies the
// endpoint with an authenticated health check before returning its port.
func NimboTrustTunnelStart(request string, controller DialerController) string {
	return trusttunnel.StartJSON(request, trustTunnelProtector(controller))
}

func NimboTrustTunnelStop() { trusttunnel.StopJSON() }

func NimboTrustTunnelStatus() string { return trusttunnel.StatusJSON() }

func NimboTrustTunnelNetworkChanged() { trusttunnel.NetworkChanged() }

// NimboTrustTunnelProbe is the real latency check: an independent client and
// one authenticated health-check round trip, then everything is closed.
func NimboTrustTunnelProbe(request string, controller DialerController) string {
	return trusttunnel.ProbeJSON(request, trustTunnelProtector(controller))
}
