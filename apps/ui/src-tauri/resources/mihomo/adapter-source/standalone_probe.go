package mihomocore

import (
	"context"
	"crypto/tls"
	"errors"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strings"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	mihomoDNS "github.com/metacubex/mihomo/dns"
	"github.com/metacubex/mihomo/log"
	yaml "go.yaml.in/yaml/v3"
)

// Nimbo Ping measures a real GET response through exactly one Mihomo outbound.
// TCP-only reachability and a HEAD/strict-204 response are not reported as success.
// Only fixed codes leave native code. In particular, errors from the subscription
// resolver and outbound may contain server addresses and must never cross IPC.
func probeFailure(err error, dial bool) error {
	code := "PROBE_GET_FAILED"
	if dial {
		code = "PROBE_DIAL_FAILED"
	}
	message := err.Error()
	switch {
	case errors.Is(err, context.DeadlineExceeded) || strings.Contains(message, "context deadline exceeded"):
		code = "PROBE_TIMEOUT"
	case strings.Contains(message, "REALITY authentication failed"):
		code = "PROBE_REALITY_AUTH_FAILED"
	case strings.Contains(message, "dns resolve failed"):
		code = "PROBE_DNS_FAILED"
	case strings.Contains(message, "platform socket protection failed") || strings.Contains(message, "mobile socket protection is required"):
		code = "PROBE_PROTECTION_FAILED"
	}
	return problem(code, "name", "outbound test failed")
}

func nimboProbeGET(ctx context.Context, proxy C.Proxy, rawURL string, expected utils.IntRanges[uint16]) (uint16, error) {
	u, err := url.Parse(rawURL)
	if err != nil {
		return 0, err
	}
	port := u.Port()
	if port == "" {
		if u.Scheme == "https" {
			port = "443"
		} else {
			port = "80"
		}
	}
	metadata := C.Metadata{}
	if err = metadata.SetRemoteAddress(net.JoinHostPort(u.Hostname(), port)); err != nil {
		return 0, err
	}
	start := time.Now()
	connection, err := proxy.DialContext(ctx, &metadata)
	if err != nil {
		return 0, probeFailure(err, true)
	}
	defer connection.Close()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return 0, err
	}
	request.Header.Set("User-Agent", "Nimbo Ping/Android")
	request.Header.Set("Cache-Control", "no-cache, no-store")
	transport := &http.Transport{
		DialContext:       func(context.Context, string, string) (net.Conn, error) { return connection, nil },
		TLSClientConfig:   &tls.Config{MinVersion: tls.VersionTLS12},
		DisableKeepAlives: true,
	}
	defer transport.CloseIdleConnections()
	client := &http.Client{Transport: transport, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	response, err := client.Do(request)
	if err != nil {
		return 0, probeFailure(err, false)
	}
	defer response.Body.Close()
	// Nimbo Ping needs only the headers. Never read a remote body into memory.
	if expected != nil && !expected.Check(uint16(response.StatusCode)) {
		return 0, problem("PROBE_HTTP_STATUS", "name", "health endpoint returned an unexpected status")
	}
	return uint16(time.Since(start).Milliseconds()), nil
}

// The VPN uses the subscription's proxy-server-nameserver for outbound hosts.
// Keep the same resolver in an isolated probe, without importing its rules,
// providers, host listeners or TUN. The physical resolver bootstraps DoH hosts.
func installAndroidProbeDNS(d *inspection, physical []mihomoDNS.NameServer, physicalHosts []any, ipv6 bool) error {
	bootstrap := mihomoDNS.NewResolver(mihomoDNS.Config{Main: physical, Default: physical, IPv6: ipv6})
	resolver.DefaultResolver, resolver.ProxyServerHostResolver, resolver.DirectHostResolver = bootstrap, bootstrap, bootstrap
	resolver.DefaultHostMapper, resolver.DefaultService = nil, nil
	dnsRoot, ok := d.root["dns"].(map[string]any)
	if !ok {
		return problem("INVALID_CONFIG", "dns", "DNS configuration is required")
	}
	projected, ok := projectAndroidDNS(dnsRoot, physicalHosts).(map[string]any)
	if !ok {
		return problem("INVALID_CONFIG", "dns", "invalid DNS configuration")
	}
	// DNS policies can refer to rule providers that the one-node probe must not
	// start or download. Outbound hostname resolution only needs these upstreams.
	probeDNS := map[string]any{"enable": true}
	for _, key := range []string{"nameserver", "default-nameserver", "proxy-server-nameserver", "prefer-h3", "ipv6"} {
		if value, exists := projected[key]; exists {
			if servers, ok := value.([]any); ok {
				independent := make([]any, 0, len(servers))
				for _, server := range servers {
					if text, ok := server.(string); ok {
						independent = append(independent, desktopProbeDNSServer(text))
					} else {
						independent = append(independent, server)
					}
				}
				value = independent
			}
			probeDNS[key] = value
		}
	}
	source, err := yaml.Marshal(map[string]any{"dns": probeDNS})
	if err != nil {
		return problem("INVALID_CONFIG", "dns", "cannot prepare probe DNS")
	}
	raw, err := config.UnmarshalRawConfig(source)
	if err != nil {
		return problem("INVALID_CONFIG", "dns", "cannot parse probe DNS")
	}
	raw.IPv6 = ipv6
	raw.DNS.IPv6 = raw.DNS.IPv6 && ipv6
	cfg, err := config.ParseRawConfig(raw)
	if err != nil {
		return problem("INVALID_CONFIG", "dns", "cannot configure probe DNS")
	}
	c := cfg.DNS
	configured := mihomoDNS.NewResolver(mihomoDNS.Config{
		Main: c.NameServer, Default: c.DefaultNameserver,
		ProxyServer: c.ProxyServerNameserver, IPv6: c.IPv6,
	})
	resolver.DefaultResolver = configured
	if configured.ProxyResolver.Invalid() {
		resolver.ProxyServerHostResolver = configured.ProxyResolver
	} else {
		resolver.ProxyServerHostResolver = configured.Resolver
	}
	resolver.DirectHostResolver = configured.Resolver
	return nil
}

// probeAndroid owns exactly one outbound and an internal physical-DNS resolver.
// It never creates a TUN, session, listener, provider ticker, routes or cache.
// The operation lock prevents constructors/resolver globals racing a VPN start.
func (m *manager) probeAndroid(r request) (any, error) {
	m.mu.Lock()
	busy := m.state != "stopped" || m.session != nil
	m.mu.Unlock()
	if busy {
		return nil, problem("BUSY", "", "standalone checks require a stopped core")
	}
	if r.TimeoutMs < 1 || r.TimeoutMs > 10000 {
		return nil, problem("INVALID_REQUEST", "timeoutMs", "expected 1..10000")
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
	if err = androidRuntimePolicy(d); err != nil {
		return nil, err
	}
	var mapping map[string]any
	for _, p := range d.DeclaredGraph.Proxies {
		if str(p, "name") == r.Name {
			mapping = p
			break
		}
	}
	if mapping == nil {
		return nil, problem("PROBE_REQUIRES_SESSION", "name", "group/provider members require a live graph")
	}
	if str(mapping, "dialer-proxy") != "" {
		return nil, problem("PROBE_REQUIRES_SESSION", "name", "chained outbound requires a live graph")
	}
	servers := []mihomoDNS.NameServer{}
	physicalHosts := []any{}
	for _, host := range r.Options.AndroidSystemDNS {
		ip, err := netip.ParseAddr(host)
		if err != nil || ip.IsUnspecified() || ip.IsMulticast() {
			return nil, problem("INVALID_ANDROID_DNS", "", "physical DNS IP required")
		}
		servers = append(servers, mihomoDNS.NameServer{Net: "udp", Addr: net.JoinHostPort(ip.String(), "53")})
		physicalHosts = append(physicalHosts, net.JoinHostPort(ip.String(), "53"))
	}
	if len(servers) == 0 {
		return nil, problem("ANDROID_DNS_REQUIRED", "", "physical DNS unavailable")
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(r.TimeoutMs)*time.Millisecond)
	defer cancel()
	m.mu.Lock()
	if expiry, ok := m.cancelledStarts[r.RequestID]; ok && time.Now().Before(expiry) {
		m.mu.Unlock()
		return nil, problem("PROBE_CANCELLED", "", "check cancelled")
	}
	m.probeRequestID, m.probeCancel = r.RequestID, cancel
	m.probeContext = ctx
	m.mu.Unlock()
	defer func() { m.mu.Lock(); m.probeRequestID = ""; m.probeCancel = nil; m.probeContext = nil; m.mu.Unlock() }()
	oldDNS, oldIPv6, oldLog := saveDNS(), resolver.DisableIPv6, log.Level()
	defer oldDNS.restore()
	defer func() { resolver.DisableIPv6 = oldIPv6; log.SetLevel(oldLog) }()
	log.SetLevel(log.SILENT)
	resolver.DisableIPv6 = !r.Options.AndroidIPv6
	if err := installAndroidProbeDNS(d, servers, physicalHosts, r.Options.AndroidIPv6); err != nil {
		return nil, err
	}
	p, err := adapter.ParseProxy(mapping)
	if err != nil {
		return nil, problem("INVALID_CONFIG", "name", "outbound could not be constructed")
	}
	defer p.Close()
	delay, err := nimboProbeGET(ctx, p, r.URL, expected)
	if ctx.Err() == context.Canceled {
		return nil, problem("PROBE_CANCELLED", "", "check cancelled")
	}
	if err != nil {
		var failure *issue
		if errors.As(err, &failure) {
			return nil, failure
		}
		return nil, problem("DELAY_FAILED", "name", "outbound test failed")
	}
	return map[string]any{"delayMs": delay, "sourceSHA256": d.SourceSHA256, "scope": "standalone-outbound", "vpnStarted": false}, nil
}
