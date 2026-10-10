package mihomocore

import (
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net"
	"net/netip"
	"net/url"
	"strings"

	RC "github.com/metacubex/mihomo/rules/common"
)

// StartAndroid is a trusted process-local entry. Caller retains borrowedFD and
// must keep it open until stop returns. Neither API1 JSON nor HTTP accepts an FD.
func StartAndroid(requestJSON string, borrowedFD int64) string {
	return invoke(requestJSON, &borrowedFD)
}

// AndroidTunPlan describes platform-owned Android plumbing. It reports compile
// support only; it is not device verification or a claim that a VPN is running.
func AndroidTunPlan() string {
	b, _ := json.Marshal(map[string]any{"apiVersion": 1, "success": true, "data": map[string]any{
		"planVersion": 2, "compiled": androidTunCompiled, "networkOwner": "android-vpn", "stack": "upstream-mihomo-sing-tun/gvisor",
		"address": "172.19.0.1/30", "routes": []string{"0.0.0.0/0", "::/0 (when dual-stack enabled)"}, "dns": []string{"172.19.0.2"}, "mtu": 1500,
		"ipv6": "optional-dual-stack", "icmp": "upstream-gvisor", "mode": "native-mihomo", "fdOwnership": "borrowed-dup-cloexec",
		"nonblockingRequired": true, "autoRoute": false, "autoRedirect": false, "interfaceDiscovery": false,
		"ruleRouting": androidTunCompiled,
		"ruleTypes":   "pinned Mihomo rule engine; process/UID classifiers remain unavailable",
		"protocols":   "pinned Mihomo v1.19.32 upstream outbound parser and dispatch", "providerUpdates": "native-configured-interval", "deviceVerified": false,
		"transportScope": "pinned Mihomo upstream proxy/protocol implementations", "applicationDNS": "pinned Mihomo DNS resolver and enhancer over VPN DNS relay", "bootstrapDNS": "protected upstream dialer",
	}})
	return string(b)
}

func mobileProxyPolicy(p map[string]any, at string) error {
	allowed := map[string]bool{"name": true, "type": true, "server": true, "port": true, "udp": true}
	var keys []string
	switch p["type"] {
	case "direct":
	case "socks5", "http":
		keys = []string{"username", "password"}
	case "vless":
		keys = []string{"uuid", "tls", "servername", "skip-cert-verify", "alpn", "network", "flow", "reality-opts", "client-fingerprint"}
		if p["udp"] == true {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".udp", "pinned VLESS defaults to XUDP; this milestone admits VLESS TCP only")
		}
		if flow, present := p["flow"]; present && flow != "" && flow != "xtls-rprx-vision" {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".flow", "only plain TCP or Vision flow admitted")
		}
		if reality, present := p["reality-opts"]; present {
			opts, ok := reality.(map[string]any)
			if !ok || p["tls"] != true {
				return problem("UNSUPPORTED_MOBILE_CONFIG", at+".reality-opts", "Reality needs a mapping and TLS")
			}
			for key := range opts {
				if key != "public-key" && key != "short-id" && key != "support-x25519mlkem768" {
					return problem("UNSUPPORTED_MOBILE_CONFIG", at+".reality-opts."+key, "unsupported Reality field")
				}
			}
			key, ok := opts["public-key"].(string)
			decoded, err := base64.RawURLEncoding.DecodeString(key)
			if !ok || err != nil || len(decoded) != 32 {
				return problem("UNSUPPORTED_MOBILE_CONFIG", at+".reality-opts.public-key", "32-byte X25519 public key required")
			}
			if short, exists := opts["short-id"]; exists {
				value, ok := short.(string)
				if !ok || len(value) > 16 || len(value)%2 != 0 {
					return problem("UNSUPPORTED_MOBILE_CONFIG", at+".reality-opts.short-id", "invalid Reality short ID")
				}
				if _, err := hex.DecodeString(value); err != nil {
					return problem("UNSUPPORTED_MOBILE_CONFIG", at+".reality-opts.short-id", "invalid Reality short ID")
				}
			}
			if flag, exists := opts["support-x25519mlkem768"]; exists {
				if _, ok := flag.(bool); !ok {
					return problem("UNSUPPORTED_MOBILE_CONFIG", at+".reality-opts.support-x25519mlkem768", "boolean required")
				}
			}
		}
	case "vmess":
		keys = []string{"uuid", "alterId", "cipher", "tls", "network", "alpn", "skip-cert-verify", "name-cert-verify", "fingerprint", "servername", "client-fingerprint", "certificate", "private-key", "global-padding", "authenticated-length"}
		if p["network"] == "ws" {
			// The pinned WebSocket adapter opens one protected TCP connection and
			// performs the upgrade on that connection. Early-data mode instead
			// creates a context.Background goroutine, so admit only plain per-call
			// upgrades without ECH, fingerprinting, or early-data options.
			keys = []string{"uuid", "alterId", "cipher", "tls", "network", "servername", "skip-cert-verify", "ws-opts"}
			if err := mobileVMessWebSocketPolicy(p["ws-opts"], at+".ws-opts"); err != nil {
				return err
			}
		}
		if p["udp"] == true {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".udp", "VMess UDP is not enabled in the TCP-only mobile profile")
		}
		if uuid, ok := p["uuid"].(string); !ok || strings.TrimSpace(uuid) == "" {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".uuid", "non-empty VMess UUID required")
		}
		if cipher, _ := p["cipher"].(string); cipher != "auto" && cipher != "none" && cipher != "zero" &&
			cipher != "aes-128-cfb" && cipher != "aes-128-gcm" && cipher != "chacha20-poly1305" {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".cipher", "unsupported VMess cipher")
		}
		alterID, exists := p["alterId"]
		value, ok := alterID.(int)
		if !exists || !ok || value < 0 || value > 65535 {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".alterId", "expected an integer from 0 to 65535")
		}
	case "trojan":
		keys = []string{"password", "sni", "skip-cert-verify", "alpn", "network"}
	case "ss":
		keys = []string{"password", "cipher"}
		switch p["cipher"] {
		case "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305":
		default:
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".cipher", "only classic AEAD ciphers audited; no SS2022/plugin/UOT lifecycle")
		}
	default:
		return problem("UNSUPPORTED_MOBILE_CONFIG", at+".type", "mobile subset: direct/socks5/http, TCP VMess/VLESS/Trojan and classic AEAD SS")
	}
	for _, k := range keys {
		allowed[k] = true
	}
	if n, exists := p["network"]; exists && n != "tcp" && n != "" {
		if p["type"] != "vmess" || n != "ws" {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".network", "only TCP and bounded VMess WebSocket/TCP are lifecycle-audited; shared grpc/xhttp transports remain denied")
		}
	}
	// No nested dialers, plugins, mux, ECH, encryption extensions
	// or transports with pooled/background sessions. Desktop schema is unchanged.
	for k := range p {
		if !allowed[k] {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+"."+k, "not in audited mobile transport subset")
		}
	}
	if server, ok := p["server"].(string); ok {
		if ip, e := netip.ParseAddr(server); e == nil && !ip.Is4() {
			return problem("UNSUPPORTED_MOBILE_CONFIG", at+".server", "IPv4 only")
		}
	}
	return nil
}

func mobileVMessWebSocketPolicy(value any, at string) error {
	bad := func(field, message string) error { return problem("UNSUPPORTED_MOBILE_CONFIG", at+field, message) }
	if value == nil {
		return nil // Mihomo's native default is a plain GET upgrade to '/'.
	}
	opts, ok := value.(map[string]any)
	if !ok {
		return bad("", "mapping required")
	}
	for key := range opts {
		if key != "path" && key != "headers" {
			return bad("."+key, "only path and bounded headers are admitted; early-data/upgrade extensions are disabled")
		}
	}
	if raw, exists := opts["path"]; exists {
		path, ok := raw.(string)
		if !ok || len(path) > 2048 {
			return bad(".path", "path must be a string of at most 2048 bytes")
		}
		parsed, err := url.Parse(path)
		if err != nil || parsed.IsAbs() || parsed.Host != "" || parsed.User != nil || parsed.Fragment != "" || (parsed.Path != "" && !strings.HasPrefix(parsed.Path, "/")) {
			return bad(".path", "expected a relative WebSocket path, not an absolute URL")
		}
		if _, earlyData := parsed.Query()["ed"]; earlyData {
			return bad(".path", "WebSocket early-data query parameter is disabled")
		}
	}
	if raw, exists := opts["headers"]; exists {
		headers, ok := raw.(map[string]any)
		if !ok || len(headers) > 16 {
			return bad(".headers", "at most 16 string headers are supported")
		}
		for name, item := range headers {
			value, ok := item.(string)
			if !ok || len(name) == 0 || len(name) > 128 || len(value) > 512 || !mobileValidHeaderName(name) || !mobileValidHeaderValue(value) {
				return bad(".headers", "header names and values must be valid and bounded")
			}
			switch strings.ToLower(name) {
			case "connection", "upgrade", "sec-websocket-key", "sec-websocket-version", "sec-websocket-protocol", "sec-websocket-extensions":
				return bad(".headers", "WebSocket handshake control headers are managed by the core")
			}
		}
	}
	return nil
}

func mobileValidHeaderName(value string) bool {
	for i := 0; i < len(value); i++ {
		c := value[i]
		if c <= 32 || c >= 127 || strings.ContainsRune("()<>@,;:\\\"/[]?={}\t", rune(c)) {
			return false
		}
	}
	return true
}

func mobileValidHeaderValue(value string) bool {
	for i := 0; i < len(value); i++ {
		if value[i] == '\r' || value[i] == '\n' || value[i] == 0 || value[i] == 127 || (value[i] < 32 && value[i] != '\t') {
			return false
		}
	}
	return true
}

func mobilePolicy(d *inspection) error {
	bad := func(at, msg string) error { return problem("UNSUPPORTED_MOBILE_CONFIG", at, msg) }
	mode := str(d.root, "mode")
	if mode != "global" && mode != "rule" {
		return bad("mode", "mobile owner supports only explicit global or rule mode")
	}
	if mode == "global" {
		for _, key := range []string{"rules", "sub-rules", "rule-providers"} {
			if _, ok := d.root[key]; ok {
				return bad(key, "global mobile mode does not execute rule graphs")
			}
		}
	} else {
		if _, ok := d.root["sub-rules"]; ok {
			return bad("sub-rules", "sub-rule dispatch is not implemented by this mobile routing slice")
		}
		list, ok := d.root["rules"].([]any)
		if !ok || len(list) == 0 {
			return bad("rules", "explicit non-empty rule list with a final MATCH action required")
		}
		for i, value := range list {
			raw, ok := value.(string)
			if !ok {
				return bad(fmt.Sprintf("rules[%d]", i), "string rule required")
			}
			if err := mobileRuleEntryPolicy(raw, true); err != nil {
				return bad(fmt.Sprintf("rules[%d]", i), err.Error())
			}
		}
		last, _ := list[len(list)-1].(string)
		kind, _, target, _ := RC.ParseRulePayload(last, true)
		if kind != "MATCH" || target == "" {
			return bad("rules", "last rule must be an explicit MATCH target; unmatched traffic is dropped")
		}
		if target == "GLOBAL" || target == "COMPATIBLE" {
			declared := false
			for _, group := range d.DeclaredGraph.Groups {
				if group["name"] == target {
					declared = true
					break
				}
			}
			if !declared {
				return bad("rules", "final MATCH cannot use an implicit upstream group")
			}
		}
		if err := mobileRuleProvidersPolicy(d); err != nil {
			return err
		}
	}
	for i, p := range d.DeclaredGraph.Proxies {
		if err := mobileProxyPolicy(p, fmt.Sprintf("proxies[%d]", i)); err != nil {
			return err
		}
	}
	hasGlobal := false
	for i, g := range d.DeclaredGraph.Groups {
		if g["name"] == "GLOBAL" {
			hasGlobal = true
		}
		groupType := str(g, "type")
		automatic := groupType == "url-test" || groupType == "fallback" || groupType == "load-balance"
		if groupType != "select" && !automatic {
			return bad(fmt.Sprintf("proxy-groups[%d].type", i), "only select, url-test, fallback and load-balance groups are supported")
		}
		if g["empty-fallback"] != "REJECT" {
			return bad(fmt.Sprintf("proxy-groups[%d].empty-fallback", i), "explicit REJECT required; upstream COMPATIBLE silently becomes direct")
		}
		if automatic {
			if rawURL := str(g, "url"); rawURL != "" {
				if len(rawURL) > 2048 || checkURL(rawURL) != nil {
					return bad(fmt.Sprintf("proxy-groups[%d].url", i), "expected bounded HTTP(S) health URL without credentials")
				}
			}
			if interval, exists := g["interval"]; exists {
				seconds, ok := interval.(int)
				if !ok || seconds < 0 || seconds > 86400 {
					return bad(fmt.Sprintf("proxy-groups[%d].interval", i), "expected 0..86400 seconds")
				}
			}
			if timeout, exists := g["timeout"]; exists {
				millis, ok := timeout.(int)
				if !ok || millis < 100 || millis > 10000 {
					return bad(fmt.Sprintf("proxy-groups[%d].timeout", i), "expected 100..10000 milliseconds")
				}
			}
			if lazy, exists := g["lazy"]; exists {
				if _, ok := lazy.(bool); !ok {
					return bad(fmt.Sprintf("proxy-groups[%d].lazy", i), "boolean required")
				}
			}
			if mobileAutoGroupContainsDirect(d, g) {
				return bad(fmt.Sprintf("proxy-groups[%d]", i), "automatic groups may not contain DIRECT or a nested route that can select DIRECT")
			}
		} else {
			for _, key := range []string{"url", "interval", "timeout", "lazy"} {
				if _, ok := g[key]; ok {
					return bad(fmt.Sprintf("proxy-groups[%d].%s", i, key), "automatic group options are valid only for url-test/fallback/load-balance")
				}
			}
		}
	}
	if mode == "global" && !hasGlobal {
		return bad("proxy-groups", "explicit GLOBAL select group required; implicit upstream GLOBAL starts DIRECT")
	}
	for name, p := range d.DeclaredGraph.Providers {
		if n, ok := p["interval"]; ok && n != 0 {
			return bad("proxy-providers."+name+".interval", "manual refresh only")
		}
		if hc, ok := p["health-check"].(map[string]any); ok && hc["enable"] == true {
			if rawURL := str(hc, "url"); rawURL == "" || len(rawURL) > 2048 || checkURL(rawURL) != nil {
				return bad("proxy-providers."+name+".health-check.url", "enabled provider checks need a bounded HTTP(S) URL without credentials")
			}
			if interval := integer(hc, "interval"); interval < 0 || interval > 86400 {
				return bad("proxy-providers."+name+".health-check.interval", "expected 0..86400 seconds")
			}
			if timeout := integer(hc, "timeout"); timeout < 0 || timeout > 10000 {
				return bad("proxy-providers."+name+".health-check.timeout", "expected 0..10000 milliseconds")
			}
		}
		for i, row := range payload(p) {
			if mobileAutomaticProviderUsed(d, name) && str(row, "type") == "direct" {
				return bad(fmt.Sprintf("proxy-providers.%s.payload[%d].type", name, i), "providers used by automatic groups may not contain DIRECT")
			}
			if err := mobileProxyPolicy(row, fmt.Sprintf("proxy-providers.%s.payload[%d]", name, i)); err != nil {
				return err
			}
		}
	}
	dns, ok := d.root["dns"].(map[string]any)
	if !ok || dns["enable"] != true {
		return bad("dns.enable", "explicit managed DNS is required")
	}
	for k, v := range dns {
		switch k {
		case "enable", "nameserver":
		case "ipv6", "use-system-hosts":
			if v != false {
				return bad("dns."+k, "must be false")
			}
		default:
			return bad("dns."+k, "first mobile DNS milestone accepts only numeric IPv4 UDP nameserver list")
		}
	}
	ns, ok := dns["nameserver"].([]any)
	if !ok || len(ns) == 0 || len(ns) > 4 {
		return bad("dns.nameserver", "one to four IPv4 UDP upstreams required")
	}
	for _, v := range ns {
		raw, ok := v.(string)
		if !ok {
			return bad("dns.nameserver", "string required")
		}
		if _, err := mobileDNSAddress(raw); err != nil {
			return bad("dns.nameserver", err.Error())
		}
	}
	return nil
}

func mobileAutomaticProviderUsed(d *inspection, target string) bool {
	groups := map[string]map[string]any{}
	for _, g := range d.DeclaredGraph.Groups {
		groups[str(g, "name")] = g
	}
	seen := map[string]bool{}
	var groupUses func(map[string]any) bool
	var routeUses func(string) bool
	groupUses = func(g map[string]any) bool {
		if g["include-all"] == true || g["include-all-providers"] == true {
			return true
		}
		for _, provider := range stringList(g["use"]) {
			if provider == target {
				return true
			}
		}
		for _, ref := range stringList(g["proxies"]) {
			if routeUses(ref) {
				return true
			}
		}
		return false
	}
	routeUses = func(ref string) bool {
		g, ok := groups[ref]
		if !ok || seen[ref] {
			return false
		}
		seen[ref] = true
		defer delete(seen, ref)
		return groupUses(g)
	}
	for _, g := range d.DeclaredGraph.Groups {
		typ := str(g, "type")
		if typ == "url-test" || typ == "fallback" || typ == "load-balance" {
			if groupUses(g) {
				return true
			}
		}
	}
	return false
}

// Automatic mobile groups must never resolve to Mihomo's DIRECT outbound.
// It would intentionally bypass the tunnel rather than fail over to another
// protected remote location. Provider payloads fetched at runtime get the same
// check in strictProxyParser before their membership is published.
func mobileAutoGroupContainsDirect(d *inspection, root map[string]any) bool {
	proxies := map[string]map[string]any{}
	for _, p := range d.DeclaredGraph.Proxies {
		proxies[str(p, "name")] = p
	}
	groups := map[string]map[string]any{}
	for _, g := range d.DeclaredGraph.Groups {
		groups[str(g, "name")] = g
	}
	providers := d.DeclaredGraph.Providers
	var hasProviderDirect func(string) bool
	hasProviderDirect = func(name string) bool {
		for _, row := range payload(providers[name]) {
			if str(row, "type") == "direct" {
				return true
			}
		}
		return false
	}
	visiting := map[string]bool{}
	var hasRouteDirect func(string) bool
	var groupHasDirect func(map[string]any) bool
	groupHasDirect = func(g map[string]any) bool {
		if g["include-all"] == true || g["include-all-proxies"] == true {
			for _, p := range proxies {
				if str(p, "type") == "direct" {
					return true
				}
			}
		}
		if g["include-all"] == true || g["include-all-providers"] == true {
			for name := range providers {
				if hasProviderDirect(name) {
					return true
				}
			}
		}
		for _, ref := range stringList(g["proxies"]) {
			if hasRouteDirect(ref) {
				return true
			}
		}
		for _, ref := range stringList(g["use"]) {
			if hasProviderDirect(ref) {
				return true
			}
		}
		return false
	}
	hasRouteDirect = func(name string) bool {
		if strings.EqualFold(name, "DIRECT") {
			return true
		}
		if p, ok := proxies[name]; ok && str(p, "type") == "direct" {
			return true
		}
		if nested, ok := groups[name]; ok {
			if visiting[name] {
				return false // cycle validation is handled by the native parser
			}
			visiting[name] = true
			defer delete(visiting, name)
			return groupHasDirect(nested)
		}
		return false
	}
	return groupHasDirect(root)
}

func stringList(value any) []string {
	list, _ := value.([]any)
	out := make([]string, 0, len(list))
	for _, item := range list {
		if text, ok := item.(string); ok {
			out = append(out, text)
		}
	}
	return out
}

func mobileRuleEntryPolicy(raw string, needTarget bool) error {
	kind, _, target, params := RC.ParseRulePayload(raw, needTarget)
	allowed := map[string]bool{
		"IP-CIDR": true, "SRC-IP-CIDR": true,
		"DST-PORT": true, "SRC-PORT": true,
		"NETWORK": true, "MATCH": true, "RULE-SET": true,
	}
	if !allowed[kind] {
		return fmt.Errorf("rule type %q needs host/process/geodata or sub-rule integration not available on mobile", kind)
	}
	if needTarget && target == "" {
		return fmt.Errorf("rule target is required")
	}
	if !needTarget && target != "" {
		return fmt.Errorf("classical provider entries cannot contain a target")
	}
	for _, param := range params {
		if param != RC.NoResolve && param != RC.Src {
			return fmt.Errorf("rule parameter %q is not admitted by the mobile owner", param)
		}
	}
	return nil
}

func mobileRuleProvidersPolicy(d *inspection) error {
	defs, _ := d.root["rule-providers"].(map[string]any)
	for name, raw := range defs {
		provider, ok := raw.(map[string]any)
		if !ok {
			return problem("UNSUPPORTED_MOBILE_CONFIG", "rule-providers."+name, "provider mapping required")
		}
		behavior := str(provider, "behavior")
		if behavior != "ipcidr" && behavior != "classical" {
			return problem("UNSUPPORTED_MOBILE_CONFIG", "rule-providers."+name+".behavior", "mobile rule providers support only IP-CIDR and audited classical rules")
		}
		if interval, ok := provider["interval"]; ok && interval != 0 {
			return problem("UNSUPPORTED_MOBILE_CONFIG", "rule-providers."+name+".interval", "mobile providers require manual refresh")
		}
		if behavior == "classical" {
			if values, exists := provider["payload"]; exists {
				rows, ok := values.([]any)
				if !ok {
					return problem("UNSUPPORTED_MOBILE_CONFIG", "rule-providers."+name+".payload", "sequence required")
				}
				for i, row := range rows {
					value, ok := row.(string)
					if !ok {
						return problem("UNSUPPORTED_MOBILE_CONFIG", fmt.Sprintf("rule-providers.%s.payload[%d]", name, i), "string rule required")
					}
					if err := mobileRuleEntryPolicy(value, false); err != nil {
						return problem("UNSUPPORTED_MOBILE_CONFIG", fmt.Sprintf("rule-providers.%s.payload[%d]", name, i), err.Error())
					}
				}
			}
		}
	}
	return nil
}

func mobileDNSAddress(raw string) (string, error) {
	raw = strings.TrimPrefix(raw, "udp://")
	if ip, e := netip.ParseAddr(raw); e == nil && ip.Is4() && !ip.IsUnspecified() {
		return net.JoinHostPort(raw, "53"), nil
	}
	ap, e := netip.ParseAddrPort(raw)
	if e == nil && ap.Addr().Is4() && !ap.Addr().IsUnspecified() && ap.Port() != 0 {
		return ap.String(), nil
	}
	return "", fmt.Errorf("numeric IPv4 UDP nameserver required; system/DHCP/DoH/DoQ/bootstrap disabled")
}
