package mihomocore

import (
	"context"
	"encoding/json"
	"io"
	"runtime"
	"strings"
	"time"
)

// Packet transport never holds manager.op while waiting. Stop can cancel a read
// immediately, even while native commands are serialized. No foreign buffer is
// retained. The generation is the one returned by a successful start response.
type ownedPacketFlow interface {
	io.Closer
	IngestPacket([]byte) error
	ReadPacket(context.Context) ([]byte, error)
}

func packetRuntimePolicy(d *inspection) error {
	if dns, ok := d.root["dns"].(map[string]any); !ok || dns["enable"] != true {
		return problem("IOS_DNS_REQUIRED", "dns.enable", "enable the source-defined native DNS resolver; no public fallback is installed")
	}
	if tun, ok := d.root["tun"].(map[string]any); ok {
		for _, key := range []string{"include-uid", "exclude-uid", "include-uid-range", "exclude-uid-range", "include-android-user", "include-package", "exclude-package", "include-interface", "exclude-interface", "route-address", "route-exclude-address", "route-address-set", "route-exclude-address-set"} {
			if androidNonZero(tun[key]) {
				return problem("UNSUPPORTED_IOS_CONFIG", "tun."+key, "NetworkExtension owns routes; app/UID/interface filters are not supported")
			}
		}
	}
	// Classical downloaded rules can contain process/UID tests after admission.
	// Domain/IP providers remain native. Never silently ignore remote classifiers.
	providers, _ := d.root["rule-providers"].(map[string]any)
	for _, raw := range providers {
		provider, _ := raw.(map[string]any)
		if provider["behavior"] == "classical" {
			return problem("UNSUPPORTED_IOS_CONFIG", "rule-providers", "classical remote rules require an iOS classifier audit; use domain/ipcidr providers")
		}
	}
	// NetworkExtension cannot classify other apps' PID/UID/packages. Do not
	// silently turn their rules into MATCH or pretend process lookup works.
	var walk func(any) error
	walk = func(value any) error {
		switch v := value.(type) {
		case string:
			u := strings.ToUpper(v)
			if strings.Contains(u, "PROCESS-") || strings.Contains(u, "UID,") || strings.Contains(u, "IN-USER,") {
				return problem("UNSUPPORTED_IOS_CONFIG", "rules", "process/UID rules are unavailable in NetworkExtension")
			}
		case []any:
			for _, child := range v {
				if err := walk(child); err != nil {
					return err
				}
			}
		case map[string]any:
			for _, child := range v {
				if err := walk(child); err != nil {
					return err
				}
			}
		}
		return nil
	}
	for _, key := range []string{"rules", "sub-rules"} {
		if err := walk(d.root[key]); err != nil {
			return err
		}
	}
	return nil
}

// StartIOSPacketFlow is distinct from the deprecated borrowed-FD entry. JSON
// alone cannot request this ownership mode. Desktop cannot create an iOS tunnel.
func StartIOSPacketFlow(input string) string {
	if runtime.GOOS != "ios" || !packetFlowCompiled {
		var r request
		_ = json.Unmarshal([]byte(input), &r)
		singleton.mu.Lock()
		gen := singleton.generation
		singleton.mu.Unlock()
		b, _ := json.Marshal(response{APIVersion: 1, RequestID: r.RequestID, Generation: gen, Error: problem("PLATFORM_UNAVAILABLE", "", "iOS packet-flow build required")})
		return string(b)
	}
	return invokeOwned(input, nil, true)
}

// WriteIOSPacket: 1 accepted, -1 stale/stopped session, -2 invalid packet.
func WriteIOSPacket(generation uint64, raw []byte) int {
	if len(raw) < 20 || len(raw) > 1500 {
		return -2
	}
	singleton.mu.Lock()
	defer singleton.mu.Unlock()
	s := singleton.session
	if singleton.state != "running" || generation != singleton.generation || s == nil || s.packet == nil || s.ctx.Err() != nil {
		return -1
	}
	if s.packet.IngestPacket(raw) != nil {
		return -2
	}
	return 1
}

// ReadIOSPacket returns an owned packet, 0 on timeout, -1 on stale/stop, -2
// invalid arguments. A packet from a previous owner can never be returned as
// current. There is no operation mutex around the blocking channel read.
func ReadIOSPacket(generation uint64, timeoutMs int) ([]byte, int) {
	if timeoutMs < 1 || timeoutMs > 1000 {
		return nil, -2
	}
	singleton.mu.Lock()
	s := singleton.session
	valid := singleton.state == "running" && generation == singleton.generation && s != nil && s.packet != nil && s.ctx.Err() == nil
	singleton.mu.Unlock()
	if !valid {
		return nil, -1
	}
	ctx, cancel := context.WithTimeout(s.ctx, time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()
	raw, err := s.packet.ReadPacket(ctx)
	singleton.mu.Lock()
	valid = singleton.session == s && singleton.state == "running" && generation == singleton.generation && s.ctx.Err() == nil
	singleton.mu.Unlock()
	if !valid {
		return nil, -1
	}
	if err == context.DeadlineExceeded {
		return nil, 0
	}
	if err != nil {
		return nil, -1
	}
	return raw, len(raw)
}
