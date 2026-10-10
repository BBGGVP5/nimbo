package mihomocore

import (
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// Four cumulative counters in the pinned upstream tracker; no destination
// history, per-connection database or polling goroutine is required.
type routeTotals interface {
	RouteTotal() (proxyUp, proxyDown, directUp, directDown int64)
}
type trafficBaseline struct{ upload, download, proxyUpload, proxyDownload, directUpload, directDownload int64 }
type trafficTelemetry struct {
	Upload         uint64 `json:"upload"`
	Download       uint64 `json:"download"`
	ProxyUpload    uint64 `json:"proxyUpload"`
	ProxyDownload  uint64 `json:"proxyDownload"`
	DirectUpload   uint64 `json:"directUpload"`
	DirectDownload uint64 `json:"directDownload"`
	RouteAvailable bool   `json:"routeAvailable"`
	TCPConnections uint64 `json:"tcpConnections"`
	UDPConnections uint64 `json:"udpConnections"`
}

func captureTrafficBaseline() trafficBaseline {
	up, down := statistic.DefaultManager.Total()
	base := trafficBaseline{upload: up, download: down}
	if routes, ok := any(statistic.DefaultManager).(routeTotals); ok {
		base.proxyUpload, base.proxyDownload, base.directUpload, base.directDownload = routes.RouteTotal()
	}
	return base
}
func counterDelta(current, base int64) uint64 {
	if current < base || current < 0 {
		return 0
	}
	return uint64(current - base)
}
func readTelemetry(s *session) trafficTelemetry {
	current := captureTrafficBaseline()
	base := s.trafficBase
	result := trafficTelemetry{Upload: counterDelta(current.upload, base.upload), Download: counterDelta(current.download, base.download)}
	if _, ok := any(statistic.DefaultManager).(routeTotals); ok {
		result.RouteAvailable = true
		result.ProxyUpload = counterDelta(current.proxyUpload, base.proxyUpload)
		result.ProxyDownload = counterDelta(current.proxyDownload, base.proxyDownload)
		result.DirectUpload = counterDelta(current.directUpload, base.directUpload)
		result.DirectDownload = counterDelta(current.directDownload, base.directDownload)
	}
	// Only aggregate protocol counts cross the wire. Never return tracker IDs,
	// names, endpoints, rules or process metadata from the telemetry operation.
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		info := c.Info()
		if info == nil || info.Metadata == nil {
			return true
		}
		switch info.Metadata.NetWork {
		case C.TCP:
			result.TCPConnections++
		case C.UDP:
			result.UDPConnections++
		}
		return true
	})
	return result
}
