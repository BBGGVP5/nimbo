package mihomocore

import (
	"io"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// Capture before committing a choice. Never tear down unrelated groups, new
// connections on the new route, or any connection for invalid/same selections.
// Mobile callers own graph.Lock across capture, Set and close; Copy never owns
// that lock. Closing occurs outside sockets.mu so relay cleanup can untrack.
func selectedGroupConnections(s *session, group string) []io.Closer {
	var connections []io.Closer
	usesGroup := func(chain C.Chain) bool {
		for _, name := range chain {
			if name == group {
				return true
			}
		}
		return false
	}
	if s.mobile != nil {
		s.mobile.mu.Lock()
		for socket := range s.mobile.sockets {
			if routed, ok := socket.(interface{ Chains() C.Chain }); ok && usesGroup(routed.Chains()) {
				connections = append(connections, socket)
			}
		}
		s.mobile.mu.Unlock()
	} else {
		// This embedded runtime owns the process-local Mihomo singleton.
		statistic.DefaultManager.Range(func(connection statistic.Tracker) bool {
			if usesGroup(connection.Info().Chain) {
				connections = append(connections, connection)
			}
			return true
		})
	}
	return connections
}

func closeGroupConnections(connections []io.Closer) {
	for _, connection := range connections {
		_ = connection.Close()
	}
}
