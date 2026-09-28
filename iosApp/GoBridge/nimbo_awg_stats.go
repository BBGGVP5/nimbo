package main

import (
	"strconv"
	"strings"
)

// UAPI may contain keys. Keep this allowlist even though awgcore currently
// filters Stats itself. Neither errors nor unexpected fields reach Swift.
func safeAWGCounters(stats string) map[string]any {
	result := make(map[string]any)
	for _, line := range strings.Split(stats, "\n") {
		key, value, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		switch key {
		case "rx_bytes", "tx_bytes", "last_handshake_time_sec":
			if number, err := strconv.ParseUint(value, 10, 64); err == nil {
				result[key] = number
			}
		}
	}
	return result
}
