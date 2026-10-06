package mihomocore

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	mihomoDNS "github.com/metacubex/mihomo/dns"
	"github.com/metacubex/mihomo/log"
	yaml "go.yaml.in/yaml/v3"
)

// A probe projection is NOT connection admission. Never load the full source as
// a runtime just to measure a node: source TUN/listeners/providers stay inert.
func desktopProbeLeaves(d *inspection, name string) ([]map[string]any, error) {
	index := map[string]map[string]any{}
	for _, entries := range [][]map[string]any{d.DeclaredGraph.Proxies, d.DeclaredGraph.Groups} {
		for _, entry := range entries {
			key := str(entry, "name")
			if key == "" || index[key] != nil {
				return nil, problem("INVALID_GRAPH", "name", "unique proxy/group names required")
			}
			index[key] = entry
		}
	}
	leaves := []map[string]any{}
	active, visited, seen := map[string]bool{}, map[string]bool{}, map[string]bool{}
	steps := 0
	var visit func(string, int) error
	visit = func(key string, depth int) error {
		steps++
		if depth > 64 || steps > 100000 || active[key] {
			return problem("INVALID_GRAPH", "name", "cyclic or oversized group")
		}
		if visited[key] {
			return nil
		}
		visited[key] = true
		entry := index[key]
		if entry == nil && (key == "DIRECT" || key == "REJECT") {
			entry = map[string]any{"name": key, "type": strings.ToLower(key)}
		}
		if entry == nil {
			return problem("NOT_FOUND", "name", "outbound not declared")
		}
		kind := str(entry, "type")
		switch kind {
		case "select", "url-test", "fallback", "load-balance":
			if androidNonZero(entry["use"]) || entry["include-all"] == true || entry["include-all-proxies"] == true || entry["include-all-providers"] == true || str(entry, "filter") != "" || str(entry, "exclude-filter") != "" {
				return problem("PROBE_REQUIRES_SESSION", "name", "dynamic group requires a live graph")
			}
			if issues := mihomoGroupIssues(entry, "proxy-groups"); len(issues) > 0 {
				return &issues[0]
			}
			members, ok := entry["proxies"].([]any)
			if !ok || len(members) == 0 {
				return problem("PROBE_REQUIRES_SESSION", "name", "no static members")
			}
			active[key] = true
			defer delete(active, key)
			for _, member := range members {
				child, ok := member.(string)
				if !ok {
					return problem("INVALID_GRAPH", "name", "string member required")
				}
				if err := visit(child, depth+1); err != nil {
					return err
				}
			}
		default:
			if (key == "DIRECT" && kind != "direct") || (key == "REJECT" && kind != "reject") {
				return problem("INVALID_GRAPH", "name", "reserved outbound name")
			}
			if kind == "rematch" || kind == "dns" || kind == "openvpn" || str(entry, "dialer-proxy") != "" {
				return problem("PROBE_REQUIRES_SESSION", "name", "outbound requires runtime ownership")
			}
			if issues := mihomoProxyIssues(entry, "proxies"); len(issues) > 0 {
				return &issues[0]
			}
			if !seen[key] {
				seen[key] = true
				leaves = append(leaves, entry)
			}
		}
		return nil
	}
	if err := visit(name, 0); err != nil {
		return nil, err
	}
	return leaves, nil
}

func installDesktopProbeDNS(d *inspection) error {
	// Only resolver settings and static hosts are parsed. This config contains no
	// subscription proxies, providers, rules, inbound endpoints or TUN settings.
	dns := map[string]any{"enable": true, "nameserver": []string{"system"}, "default-nameserver": []string{"system"}}
	if original, ok := d.root["dns"].(map[string]any); ok {
		for _, key := range []string{"nameserver", "default-nameserver", "proxy-server-nameserver", "prefer-h3", "ipv6"} {
			if value, exists := original[key]; exists {
				if servers, ok := value.([]any); ok {
					for _, server := range servers {
						if s, ok := server.(string); ok && strings.Contains(s, "#") {
							return problem("PROBE_REQUIRES_SESSION", "dns", "routed DNS requires a live graph")
						}
					}
				}
				dns[key] = value
			}
		}
	}
	projection := map[string]any{"dns": dns}
	if hosts, exists := d.root["hosts"]; exists {
		projection["hosts"] = hosts
	}
	source, err := yaml.Marshal(projection)
	if err != nil {
		return problem("INVALID_CONFIG", "dns", "cannot prepare probe DNS")
	}
	raw, err := config.UnmarshalRawConfig(source)
	if err != nil {
		return problem("INVALID_CONFIG", "dns", "cannot parse probe DNS")
	}
	raw.DNS.FallbackFilter.GeoIP = false
	raw.IPv6 = true
	raw.Profile.StoreSelected = false
	raw.Tun.Enable = false
	cfg, err := config.ParseRawConfig(raw)
	if err != nil {
		return problem("INVALID_CONFIG", "dns", "cannot construct probe DNS")
	}
	defer closeConfig(cfg)
	c := cfg.DNS
	configured := mihomoDNS.NewResolver(mihomoDNS.Config{Main: c.NameServer, Default: c.DefaultNameserver, ProxyServer: c.ProxyServerNameserver, IPv6: c.IPv6})
	resolver.DefaultResolver = configured
	if configured.ProxyResolver.Invalid() {
		resolver.ProxyServerHostResolver = configured.ProxyResolver
	} else {
		resolver.ProxyServerHostResolver = configured.Resolver
	}
	resolver.DirectHostResolver = configured.Resolver
	resolver.DefaultHostMapper = nil
	resolver.DefaultService = nil
	resolver.DefaultHosts = resolver.NewHosts(cfg.Hosts)
	return nil
}

func (m *manager) probeDesktop(r request) (any, error) {
	m.mu.Lock()
	busy := m.state != "stopped" || m.session != nil
	m.mu.Unlock()
	if busy {
		return nil, problem("BUSY", "", "standalone checks require stopped ownership")
	}
	if r.TimeoutMs < 100 || r.TimeoutMs > 30000 || r.Name == "" || len(r.Name) > 1024 {
		return nil, problem("INVALID_REQUEST", "", "invalid probe limits")
	}
	if err := checkURL(r.URL); err != nil {
		return nil, err
	}
	expected, err := utils.NewUnsignedRanges[uint16](r.ExpectedStatus)
	if err != nil {
		return nil, problem("INVALID_REQUEST", "expectedStatus", "invalid status range")
	}
	d, err := inspect(r.YAML)
	if err != nil {
		return nil, err
	}
	leaves, err := desktopProbeLeaves(d, r.Name)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(r.TimeoutMs)*time.Millisecond)
	defer cancel()
	m.mu.Lock()
	if expiry, ok := m.cancelledStarts[r.RequestID]; ok && time.Now().Before(expiry) {
		m.mu.Unlock()
		return nil, problem("PROBE_CANCELLED", "", "check cancelled")
	}
	m.probeRequestID, m.probeCancel, m.probeContext = r.RequestID, cancel, ctx
	m.mu.Unlock()
	defer func() { m.mu.Lock(); m.probeRequestID = ""; m.probeCancel = nil; m.probeContext = nil; m.mu.Unlock() }()
	oldDNS, oldIPv6, oldLog := saveDNS(), resolver.DisableIPv6, log.Level()
	defer oldDNS.restore()
	defer func() { resolver.DisableIPv6 = oldIPv6; log.SetLevel(oldLog) }()
	log.SetLevel(log.SILENT)
	resolver.DisableIPv6 = false
	if err := installDesktopProbeDNS(d); err != nil {
		return nil, err
	}
	// Static nested groups test the first healthy declared leaf, without a native
	// selector, health ticker, persisted choice or claim of an active group route.
	var failure error = problem("DELAY_FAILED", "name", "no healthy declared outbound")
	for _, mapping := range leaves {
		if ctx.Err() != nil {
			break
		}
		p, err := adapter.ParseProxy(mapping)
		if err != nil {
			return nil, problem("INVALID_CONFIG", "name", "outbound construction failed")
		}
		delay, err := nimboProbeGET(ctx, p, r.URL, expected)
		_ = p.Close()
		if ctx.Err() != nil {
			break
		}
		if err == nil {
			return map[string]any{"delayMs": delay, "sourceSHA256": d.SourceSHA256, "scope": "desktop-offline-probe", "vpnStarted": false}, nil
		}
		failure = err
	}
	if ctx.Err() == context.Canceled {
		return nil, problem("PROBE_CANCELLED", "", "check cancelled")
	}
	if ctx.Err() == context.DeadlineExceeded {
		return nil, problem("PROBE_TIMEOUT", "name", "check timed out")
	}
	var safe *issue
	if errors.As(failure, &safe) {
		return nil, safe
	}
	return nil, problem("DELAY_FAILED", "name", "outbound check failed")
}
