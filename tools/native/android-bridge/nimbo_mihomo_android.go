//go:build android

package libXray

import mihomo "nimbo/mihomocore"

type NimboMihomoFlowOwner interface {
	Resolve(network, source string, sourcePort int64, destination string, destinationPort int64) string
}

func NimboMihomoSetFlowOwnerResolver(callback NimboMihomoFlowOwner) string {
	return mihomo.SetFlowOwnerResolver(callback)
}

// NimboMihomoAndroidTunPlan reports compiled capabilities, not device readiness.
func NimboMihomoAndroidTunPlan() string { return mihomo.AndroidTunPlan() }

// NimboMihomoStartAndroid receives an already-established nonblocking TUN.
// Native code owns only its duplicate; the service retains the original until
// stop has returned. Descriptors are never accepted from YAML or remote JSON.
func NimboMihomoStartAndroid(request string, borrowedFD int64) string {
	return mihomo.StartAndroid(request, borrowedFD)
}

type nimboMihomoProtector struct{ controller DialerController }

func (p nimboMihomoProtector) Protect(fd int64) bool {
	return p.controller.ProtectFd(int(fd))
}

// NimboMihomoSetSocketProtector must be called while stopped. The retained
// gomobile interface keeps the VpnService callback alive until native shutdown.
// The callback must not re-enter a native lifecycle operation.
func NimboMihomoSetSocketProtector(controller DialerController) string {
	if controller == nil {
		return mihomo.SetSocketProtector(nil)
	}
	return mihomo.SetSocketProtector(nimboMihomoProtector{controller})
}
