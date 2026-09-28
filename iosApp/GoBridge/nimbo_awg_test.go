package main

import "testing"

func TestAWGStatsNeverReturnConfiguration(t *testing.T) {
	stats := safeAWGCounters("private_key=SECRET\npublic_key=PEER\nendpoint=HOST\nrx_bytes=42\ntx_bytes=7\nlast_handshake_time_sec=0\nlast_handshake_time_nsec=bad\n")
	if len(stats) != 3 || stats["rx_bytes"] != uint64(42) || stats["tx_bytes"] != uint64(7) || stats["last_handshake_time_sec"] != uint64(0) {
		t.Fatal("unexpected safe statistics")
	}
}
