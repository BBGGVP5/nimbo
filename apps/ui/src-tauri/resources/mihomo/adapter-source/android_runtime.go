package mihomocore

import (
	"fmt"
	yaml "go.yaml.in/yaml/v3"
	"net"
	"net/netip"
	"reflect"
	"strings"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/inbound"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/component/geodata"
	mihomoHTTP "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/component/keepalive"
	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/component/resource"
	"github.com/metacubex/mihomo/config"
	// Mihomo's config.ParseRawConfig uses a linkname implemented by executor.
	C "github.com/metacubex/mihomo/constant"
	_ "github.com/metacubex/mihomo/hub/executor"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/log"
)

// androidRuntimePolicy is intentionally about ownership, not a reduced proxy
// protocol/rule allow-list. The upstream parser and tunnel own all traffic
// semantics; Android alone owns the VPN descriptor, routes and app selection.
func androidRuntimePolicy(d *inspection) error {
	if value, exists := d.root["enable-process"]; exists {
		if _, ok := value.(bool); !ok {
			return problem("INVALID_CONFIG", "enable-process", "boolean required")
		}
	}
	schemaRoot := make(map[string]any, len(d.root))
	for k, v := range d.root {
		if k != "enable-process" {
			schemaRoot[k] = v
		}
	}
	fields := tagFields(reflect.TypeOf(config.RawConfig{}), "yaml")
	for key := range schemaRoot {
		if _, ok := fields[key]; !ok {
			return problem("UNSUPPORTED_ANDROID_CONFIG", key, "unknown Mihomo top-level key; nothing was silently discarded")
		}
	}
	bad := func(path, message string) error {
		return problem("UNSUPPORTED_ANDROID_CONFIG", path, message)
	}
	if err := validateAndroidRawStruct(schemaRoot, reflect.TypeOf(config.RawConfig{}), "yaml", "", nil); err != nil {
		return err
	}
	for _, key := range []string{
		"external-controller", "external-controller-tls", "external-controller-unix",
		"external-controller-pipe", "external-ui", "external-ui-url", "external-ui-name",
		"external-doh-server", "secret",
	} {
		if value, exists := d.root[key]; exists && value != "" {
			return bad(key, "host listener/UI is not owned by the Android VPN runtime")
		}
	}
	for _, key := range []string{
		"ss-config", "vmess-config", "authentication", "skip-auth-prefixes",
	} {
		if androidNonZero(d.root[key]) {
			return bad(key, "Mihomo inbound/listener settings are owned by Android VpnService and cannot be applied")
		}
	}
	for _, key := range []string{"inbound-tfo", "inbound-mptcp"} {
		if d.root[key] == true {
			return bad(key, "there are no Mihomo inbound sockets in Android VPN-client mode")
		}
	}
	if integer(d.root, "external-controller-routing-mark") != 0 {
		return bad("external-controller-routing-mark", "host routing marks are not available inside Android VpnService")
	}
	if androidNonZero(d.root["external-controller-cors"]) {
		return bad("external-controller-cors", "there is no Mihomo external controller in Android VPN-client mode")
	}
	if value, exists := d.root["interface-name"]; exists && value != "" {
		return bad("interface-name", "physical interface binding belongs to Android Network binding")
	}
	if integer(d.root, "routing-mark") != 0 {
		return bad("routing-mark", "host routing marks are not available inside Android VpnService")
	}
	if list, ok := d.root["listeners"].([]any); ok && len(list) > 0 {
		return bad("listeners", "Mihomo inbound listeners are not started by the Android VPN adapter")
	}
	if list, ok := d.root["tunnels"].([]any); ok && len(list) > 0 {
		return bad("tunnels", "host-side tunnel interfaces are owned by Android VpnService")
	}
	for _, key := range []string{"tuic-server", "ntp", "iptables"} {
		if androidNonZero(d.root[key]) {
			return bad(key, "host listeners, system time, firewall and route policy are owned by Android")
		}
	}
	if d.root["geo-auto-update"] == true {
		return bad("geo-auto-update", "background geodata updater is not lifecycle-managed by the Android owner")
	}
	if d.root["geodata-mode"] == false {
		return bad("geodata-mode", "this Android build bundles Mihomo GeoIP.dat, not a host MMDB; use geodata-mode: true")
	}
	if integer(d.root, "geo-update-interval") != 0 {
		return bad("geo-update-interval", "the Android runtime does not run Mihomo's background geodata updater")
	}
	if androidNonZero(d.root["global-client-fingerprint"]) {
		return bad("global-client-fingerprint", "this Mihomo setting was removed upstream; set client-fingerprint on each proxy")
	}
	for i, proxy := range d.DeclaredGraph.Proxies {
		at := fmt.Sprintf("proxies[%d]", i)
		if issues := mihomoProxyIssues(proxy, at); len(issues) > 0 {
			return &issues[0]
		}
		if _, exists := proxy["interface-name"]; exists {
			return bad(at+".interface-name", "per-outbound physical interface binding is owned by Android Network")
		}
		if _, exists := proxy["routing-mark"]; exists {
			return bad(at+".routing-mark", "host routing marks are not available inside Android VpnService")
		}
	}
	for i, group := range d.DeclaredGraph.Groups {
		if issues := mihomoGroupIssues(group, fmt.Sprintf("proxy-groups[%d]", i)); len(issues) > 0 {
			return &issues[0]
		}
	}
	for name, provider := range d.DeclaredGraph.Providers {
		path := "proxy-providers." + name
		if issues := androidProxyProviderIssues(provider, path); len(issues) > 0 {
			return &issues[0]
		}
		if override, ok := provider["override"].(map[string]any); ok {
			for _, key := range []string{"interface-name", "routing-mark"} {
				if _, exists := override[key]; exists {
					return bad(path+".override."+key, "physical interface and host routing policy are owned by Android")
				}
			}
			if expressions, ok := override["override-expr"].([]any); ok {
				for i, raw := range expressions {
					expression, _ := raw.(string)
					lower := strings.ToLower(expression)
					if strings.Contains(lower, "interface-name") || strings.Contains(lower, "routing-mark") {
						return bad(fmt.Sprintf("%s.override.override-expr[%d]", path, i), "physical interface and host routing policy are owned by Android")
					}
				}
			}
		}
	}
	if providers, ok := d.root["rule-providers"].(map[string]any); ok {
		for name, raw := range providers {
			provider, ok := raw.(map[string]any)
			if !ok {
				return bad("rule-providers."+name, "mapping required")
			}
			if issues := androidRuleProviderIssues(provider, "rule-providers."+name); len(issues) > 0 {
				return &issues[0]
			}
		}
	}
	checkRules := func(value any, path string) error {
		rows, ok := value.([]any)
		if !ok {
			return bad(path, "rule list must be a sequence")
		}
		for i, raw := range rows {
			_, ok := raw.(string)
			if !ok {
				return bad(fmt.Sprintf("%s[%d]", path, i), "rule must be a string")
			}

		}
		return nil
	}
	if rows, exists := d.root["rules"]; exists {
		if err := checkRules(rows, "rules"); err != nil {
			return err
		}
	}
	if groups, ok := d.root["sub-rules"].(map[string]any); ok {
		for name, rows := range groups {
			if err := checkRules(rows, "sub-rules."+name); err != nil {
				return err
			}
		}
	}
	if value, ok := d.root["dns"].(map[string]any); !ok || value["enable"] != true {
		return bad("dns.enable", "enable Mihomo DNS so the Android VPN DNS address is served by the core")
	} else if value["listen"] != nil && value["listen"] != "" {
		return bad("dns.listen", "Android DNS is served on the VPN interface; a separate host listener is forbidden")
	}
	// Never let a Mihomo resolver ask Android's system resolver or DHCP for an
	// upstream. Those APIs can select a different physical network on multi-homed
	// devices and do not pass through Nimbo's protected, network-bound dialer.
	for _, key := range []string{"default-nameserver", "proxy-server-nameserver", "direct-nameserver", "proxy-server-nameserver-policy"} {
		if value, exists := d.root[key]; exists && androidUsesUnownedDNS(value) {
			return bad(key, "system/DHCP DNS is not owned by the VPN; use explicit upstream IP/DoH servers")
		}
	}
	if dns, ok := d.root["dns"].(map[string]any); ok {
		if integer(dns, "listen-routing-mark") != 0 {
			return bad("dns.listen-routing-mark", "host DNS listener routing marks are not available inside Android VpnService")
		}
		for key, value := range dns {
			if strings.Contains(strings.ToLower(key), "nameserver") && androidUsesUnownedDNS(value) {
				return bad("dns."+key, "system/DHCP DNS is not owned by the VPN; use explicit upstream IP/DoH servers")
			}
		}
		if dns["append-system-dns"] == true {
			return bad("dns.append-system-dns", "system DNS cannot be appended to the Android VPN resolver")
		}
	}
	if value, ok := d.root["tun"].(map[string]any); ok {
		for field, setting := range value {
			switch field {
			case "enable":
				if enabled, ok := setting.(bool); !ok || !enabled {
					return bad("tun.enable", "Android VPN mode requires the Mihomo TUN dataplane")
				}
			case "stack":
				if setting != "gvisor" {
					return bad("tun.stack", "Android VPN uses Mihomo's gVisor stack")
				}
			case "dns-hijack":
				if _, ok := setting.([]any); !ok {
					return bad("tun.dns-hijack", "sequence required")
				}
			case "auto-route", "auto-detect-interface", "strict-route":
				if _, ok := setting.(bool); !ok {
					return bad("tun."+field, "boolean required")
				}
			case "exclude-package":
				rows, ok := setting.([]any)
				if !ok {
					return bad("tun.exclude-package", "sequence required")
				}
				for _, row := range rows {
					if name, ok := row.(string); !ok || name == "" {
						return bad("tun.exclude-package", "package name required")
					}
				}
			case "endpoint-independent-nat", "udp-timeout", "icmp-timeout", "disable-icmp-forwarding":
				// These affect Mihomo's gVisor dataplane and are retained by the
				// Android projection; all route/interface ownership stays native.
			default:
				return bad("tun."+field, "TUN address, route, package, interface and auto-route settings belong to Android VpnService")
			}
		}
	}
	if androidNonZero(d.root["experimental"]) {
		return bad("experimental", "experimental process-global toggles are not changed by the Android runtime")
	}
	if androidNonZero(d.root["tls"]) {
		return bad("tls", "custom inbound TLS certificates are not used by Android VPN client mode")
	}
	if androidNonZero(d.root["clash-for-android"]) {
		return bad("clash-for-android", "system-DNS injection and client UI metadata are not applied by Nimbo")
	}
	return nil
}

// RawConfig contains several open-ended maps by design (proxies, groups,
// providers, hosts and DNS policies). Validate every ordinary nested struct
// against the pinned upstream YAML schema while leaving only those explicitly
// handled dynamic maps open.
func validateAndroidRawStruct(value any, schema reflect.Type, tag, path string, dynamic map[string]bool) error {
	if dynamic != nil && dynamic["*"] {
		return nil
	}
	for schema.Kind() == reflect.Pointer {
		schema = schema.Elem()
	}
	values, ok := value.(map[string]any)
	if !ok {
		if value == nil {
			return nil
		}
		return problem("INVALID_CONFIG", pathOrRoot(path), "mapping required")
	}
	fields := tagFields(schema, tag)
	for key, child := range values {
		field, exists := fields[key]
		if !exists {
			return problem("UNSUPPORTED_ANDROID_CONFIG", joinConfigPath(path, key), "unknown Mihomo field; nothing was silently discarded")
		}
		if dynamic != nil && dynamic[key] {
			continue
		}
		for field.Kind() == reflect.Pointer {
			field = field.Elem()
		}
		if field.Kind() == reflect.Struct {
			if err := validateAndroidRawStruct(child, field, tag, joinConfigPath(path, key), androidDynamicStructFields(path, key)); err != nil {
				return err
			}
		}
	}
	return nil
}

func androidDynamicStructFields(path, key string) map[string]bool {
	if path == "dns" && (key == "nameserver-policy" || key == "proxy-server-nameserver-policy") {
		return map[string]bool{"*": true}
	}
	return nil
}

func pathOrRoot(path string) string {
	if path == "" {
		return "$"
	}
	return path
}

func joinConfigPath(path, key string) string {
	if path == "" {
		return key
	}
	return path + "." + key
}

func androidNonZero(value any) bool {
	switch v := value.(type) {
	case nil:
		return false
	case bool:
		return v
	case string:
		return strings.TrimSpace(v) != ""
	case int:
		return v != 0
	case int64:
		return v != 0
	case uint64:
		return v != 0
	case float64:
		return v != 0
	case []any:
		return len(v) > 0
	case map[string]any:
		for _, child := range v {
			if androidNonZero(child) {
				return true
			}
		}
	}
	return false
}

type androidProviderHealthSchema struct {
	Enable         bool   `provider:"enable"`
	URL            string `provider:"url,omitempty"`
	Interval       int    `provider:"interval,omitempty"`
	Timeout        int    `provider:"timeout,omitempty"`
	Lazy           bool   `provider:"lazy,omitempty"`
	ExpectedStatus string `provider:"expected-status,omitempty"`
}

type androidProviderOverrideNameSchema struct {
	Pattern string `provider:"pattern"`
	Target  string `provider:"target"`
}

type androidProviderOverrideSchema struct {
	TFO              *bool                               `provider:"tfo,omitempty"`
	MPTCP            *bool                               `provider:"mptcp,omitempty"`
	UDP              *bool                               `provider:"udp,omitempty"`
	UDPOverTCP       *bool                               `provider:"udp-over-tcp,omitempty"`
	Up               *string                             `provider:"up,omitempty"`
	Down             *string                             `provider:"down,omitempty"`
	DialerProxy      *string                             `provider:"dialer-proxy,omitempty"`
	SkipCertVerify   *bool                               `provider:"skip-cert-verify,omitempty"`
	NameCertVerify   *string                             `provider:"name-cert-verify,omitempty"`
	InterfaceName    *string                             `provider:"interface-name,omitempty"`
	RoutingMark      *int                                `provider:"routing-mark,omitempty"`
	IPVersion        *string                             `provider:"ip-version,omitempty"`
	AdditionalPrefix *string                             `provider:"additional-prefix,omitempty"`
	AdditionalSuffix *string                             `provider:"additional-suffix,omitempty"`
	ProxyName        []androidProviderOverrideNameSchema `provider:"proxy-name,omitempty"`
	OverrideExpr     []string                            `provider:"override-expr,omitempty"`
}

type androidProxyProviderSchema struct {
	Type          string                        `provider:"type"`
	Path          string                        `provider:"path,omitempty"`
	URL           string                        `provider:"url,omitempty"`
	Proxy         string                        `provider:"proxy,omitempty"`
	Interval      int                           `provider:"interval,omitempty"`
	Filter        string                        `provider:"filter,omitempty"`
	ExcludeFilter string                        `provider:"exclude-filter,omitempty"`
	ExcludeType   string                        `provider:"exclude-type,omitempty"`
	DialerProxy   string                        `provider:"dialer-proxy,omitempty"`
	SizeLimit     int64                         `provider:"size-limit,omitempty"`
	Payload       []map[string]any              `provider:"payload,omitempty"`
	AgeSecretKey  string                        `provider:"age-secret-key,omitempty"`
	HealthCheck   androidProviderHealthSchema   `provider:"health-check,omitempty"`
	Override      androidProviderOverrideSchema `provider:"override,omitempty"`
	Header        map[string][]string           `provider:"header,omitempty"`
}

func androidProxyProviderIssues(provider map[string]any, path string) []issue {
	if issues := checkAndroidStructuredFields(provider, reflect.TypeOf(androidProxyProviderSchema{}), "provider", path); len(issues) > 0 {
		return issues
	}
	if provider["type"] != "http" && provider["type"] != "file" && provider["type"] != "inline" {
		return []issue{{"UNSUPPORTED_ANDROID_CONFIG", "expected native Mihomo http/file/inline provider", path + ".type"}}
	}
	if provider["type"] == "http" {
		if err := checkURL(str(provider, "url")); err != nil {
			return []issue{{"INVALID_CONFIG", "explicit HTTP(S) URL without userinfo required", path + ".url"}}
		}
	}
	for _, key := range []string{"interval", "size-limit"} {
		if value, exists := provider[key]; exists {
			n, ok := numericConfig(value)
			if !ok || n < 0 {
				return []issue{{"INVALID_CONFIG", "must be a nonnegative number", path + "." + key}}
			}
		}
	}
	if health, ok := provider["health-check"].(map[string]any); ok {
		for _, key := range []string{"interval", "timeout"} {
			if value, exists := health[key]; exists {
				n, ok := numericConfig(value)
				if !ok || n < 0 {
					return []issue{{"INVALID_CONFIG", "must be a nonnegative number", path + ".health-check." + key}}
				}
			}
		}
	}
	if overrides, ok := provider["override"].(map[string]any); ok {
		for _, key := range []string{"interface-name", "routing-mark"} {
			if _, exists := overrides[key]; exists {
				return []issue{{"UNSUPPORTED_ANDROID_CONFIG", "physical interface and host routing policy are owned by Android", path + ".override." + key}}
			}
		}
	}
	if payload, ok := provider["payload"].([]any); ok {
		for i, raw := range payload {
			proxy, ok := raw.(map[string]any)
			if !ok {
				return []issue{{"INVALID_CONFIG", "proxy mapping required", fmt.Sprintf("%s.payload[%d]", path, i)}}
			}
			if issues := mihomoProxyIssues(proxy, fmt.Sprintf("%s.payload[%d]", path, i)); len(issues) > 0 {
				return issues
			}
			for _, key := range []string{"interface-name", "routing-mark"} {
				if _, exists := proxy[key]; exists {
					return []issue{{"UNSUPPORTED_ANDROID_CONFIG", "physical interface and host routing policy are owned by Android", fmt.Sprintf("%s.payload[%d].%s", path, i, key)}}
				}
			}
		}
	}
	return nil
}

func numericConfig(value any) (float64, bool) {
	switch v := value.(type) {
	case int:
		return float64(v), true
	case int64:
		return float64(v), true
	case uint64:
		return float64(v), true
	case float64:
		return v, true
	default:
		return 0, false
	}
}

func checkAndroidStructuredFields(value map[string]any, schema reflect.Type, tag, path string) []issue {
	fields := tagFields(schema, tag)
	issues := []issue{}
	for key, child := range value {
		field, exists := fields[key]
		if !exists {
			issues = append(issues, issue{"UNSUPPORTED_ANDROID_CONFIG", "unknown native Mihomo field", path + "." + key})
			continue
		}
		for field.Kind() == reflect.Pointer {
			field = field.Elem()
		}
		if field.Kind() == reflect.Struct {
			if nested, ok := child.(map[string]any); ok {
				issues = append(issues, checkAndroidStructuredFields(nested, field, tag, path+"."+key)...)
			}
		} else if field.Kind() == reflect.Slice && field.Elem().Kind() == reflect.Struct {
			if rows, ok := child.([]any); ok {
				for i, row := range rows {
					if nested, ok := row.(map[string]any); ok {
						issues = append(issues, checkAndroidStructuredFields(nested, field.Elem(), tag, fmt.Sprintf("%s.%s[%d]", path, key, i))...)
					}
				}
			}
		}
	}
	return issues
}

func mihomoGroupIssues(group map[string]any, path string) []issue {
	fields := tagFields(reflect.TypeOf(outboundgroup.GroupCommonOption{}), "group")
	var specific any
	switch group["type"] {
	case "select":
		specific = outboundgroup.SelectorOption{}
	case "url-test":
		specific = outboundgroup.URLTestOption{}
	case "fallback":
		specific = outboundgroup.FallbackOption{}
	case "load-balance":
		specific = outboundgroup.LoadBalanceOption{}
	default:
		return []issue{{"UNSUPPORTED_ANDROID_CONFIG", "group type is unavailable in pinned Mihomo", path + ".type"}}
	}
	for key, value := range tagFields(reflect.TypeOf(specific), "group") {
		fields[key] = value
	}
	issues := checkMihomoFields(group, fields, "group", path)
	for _, key := range []string{"filter", "exclude-filter"} {
		if raw, ok := group[key].(string); ok && raw != "" {
			if _, err := compileFilters(raw); err != nil {
				issues = append(issues, issue{"INVALID_CONFIG", "invalid native Mihomo regular expression", path + "." + key})
			}
		}
	}
	return issues
}

func androidUsesSystemDNS(value any) bool {
	switch v := value.(type) {
	case string:
		s := strings.ToLower(strings.TrimSpace(v))
		return s == "system" || strings.HasPrefix(s, "system://") || strings.HasPrefix(s, "dhcp://")
	case []any:
		for _, item := range v {
			if androidUsesSystemDNS(item) {
				return true
			}
		}
	case map[string]any:
		for _, item := range v {
			if androidUsesSystemDNS(item) {
				return true
			}
		}
	}
	return false
}

// Exact "system" is resolved from the protected physical Network by Android.
// DHCP and interface-selected pseudo-resolvers still have no safe platform owner.
func androidUsesUnownedDNS(value any) bool {
	switch v := value.(type) {
	case string:
		s := strings.ToLower(strings.TrimSpace(v))
		return strings.HasPrefix(s, "system://") || strings.HasPrefix(s, "dhcp://")
	case []any:
		for _, item := range v {
			if androidUsesUnownedDNS(item) {
				return true
			}
		}
	case map[string]any:
		for _, item := range v {
			if androidUsesUnownedDNS(item) {
				return true
			}
		}
	}
	return false
}
func projectAndroidDNS(value any, servers []any) any {
	switch v := value.(type) {
	case string:
		if strings.EqualFold(strings.TrimSpace(v), "system") {
			return servers
		}
		return v
	case []any:
		result := []any{}
		for _, item := range v {
			projected := projectAndroidDNS(item, servers)
			if _, scalar := item.(string); scalar {
				if list, ok := projected.([]any); ok {
					result = append(result, list...)
					continue
				}
			}
			result = append(result, projected)
		}
		return result
	case map[string]any:
		result := map[string]any{}
		for k, item := range v {
			result[k] = projectAndroidDNS(item, servers)
		}
		return result
	}
	return value
}

// Parse using Mihomo's full configuration model. Android IPv6 is a platform
// choice passed separately from YAML; the YAML is not rewritten or rehashed.
func nativeParseAndroid(d *inspection, ipv6 bool, physicalDNS ...[]string) (*config.Config, error) {
	oldLogLevel := log.Level()
	defer log.SetLevel(oldLogLevel)
	log.SetLevel(log.SILENT)
	source, err := effectiveYAML(d)
	if err != nil {
		return nil, err
	}
	if androidUsesSystemDNS(d.root["dns"]) {
		servers := []any{}
		if len(physicalDNS) > 0 {
			for _, host := range physicalDNS[0] {
				ip, err := netip.ParseAddr(host)
				if err != nil || ip.IsUnspecified() || ip.IsMulticast() {
					return nil, problem("INVALID_ANDROID_DNS", "", "physical DNS IP required")
				}
				servers = append(servers, net.JoinHostPort(ip.String(), "53"))
			}
		}
		if len(servers) == 0 {
			return nil, problem("ANDROID_DNS_REQUIRED", "", "physical Network DNS servers unavailable")
		}
		projected := map[string]any{}
		for k, v := range d.root {
			projected[k] = v
		}
		projected["dns"] = projectAndroidDNS(d.root["dns"], servers)
		var err error
		source, err = yaml.Marshal(projected)
		if err != nil {
			return nil, problem("INVALID_CONFIG", "dns", "cannot project physical DNS")
		}
	}
	raw, err := config.UnmarshalRawConfig(source)
	if err != nil {
		return nil, problem("INVALID_CONFIG", "$", err.Error())
	}
	raw.IPv6 = ipv6
	raw.DNS.IPv6 = raw.DNS.IPv6 && ipv6
	// Android packages the pinned GeoIP.dat/GeoSite.dat databases in the app.
	// Mihomo's default MMDB mode expects a separate host-managed database that
	// Nimbo does not ship; make the bundled upstream-compatible data the default.
	raw.GeodataMode = true
	raw.FindProcessMode = process.FindProcessStrict
	if d.root["enable-process"] == false || d.root["find-process-mode"] == "off" {
		raw.FindProcessMode = process.FindProcessOff
	}
	// The service owns interfaces; never open source-defined host listeners.
	raw.Port = 0
	raw.SocksPort = 0
	raw.RedirPort = 0
	raw.TProxyPort = 0
	raw.MixedPort = 0
	raw.AllowLan = false
	raw.LogLevel = log.SILENT
	if serialized, marshalErr := yaml.Marshal(raw); marshalErr == nil {
		d.finalConfig, _ = decodeDocument(string(serialized))
	}
	cfg, err := config.ParseRawConfig(raw)
	if err != nil {
		return nil, problem("INVALID_CONFIG", "$", fmt.Sprintf("Mihomo parser: %v", err))
	}
	return cfg, nil
}

// androidTunProjection replaces host-owned interface controls after parsing.
// Protocols, resolver, DNS hijack, rules and groups are still Mihomo-native.
func androidTunProjection(source LC.Tun, fd int, ipv6 bool) LC.Tun {
	options := source
	options.Enable = true
	options.Device = ""
	options.Stack = C.TunGvisor
	options.AutoRoute = false
	options.AutoDetectInterface = false
	options.AutoRedirect = false
	options.MTU = 1500
	options.GSO = false
	options.GSOMaxSize = 0
	options.Inet4Address = []netip.Prefix{netip.MustParsePrefix("172.19.0.1/30")}
	options.Inet6Address = nil
	if ipv6 {
		options.Inet6Address = []netip.Prefix{netip.MustParsePrefix("fdfe:dcba:9876::1/126")}
	}
	options.IPRoute2TableIndex = 0
	options.IPRoute2RuleIndex = 0
	options.AutoRedirectInputMark = 0
	options.AutoRedirectOutputMark = 0
	options.AutoRedirectIPRoute2FallbackRuleIndex = 0
	options.LoopbackAddress = nil
	options.StrictRoute = false
	options.RouteAddress = nil
	options.RouteAddressSet = nil
	options.RouteExcludeAddress = nil
	options.RouteExcludeAddressSet = nil
	options.Inet4RouteAddress = nil
	options.Inet6RouteAddress = nil
	options.Inet4RouteExcludeAddress = nil
	options.Inet6RouteExcludeAddress = nil
	options.IncludeInterface = nil
	options.ExcludeInterface = nil
	options.IncludeUID = nil
	options.IncludeUIDRange = nil
	options.ExcludeUID = nil
	options.ExcludeUIDRange = nil
	options.IncludeAndroidUser = nil
	options.IncludePackage = nil
	options.ExcludePackage = nil
	options.IncludeMACAddress = nil
	options.ExcludeMACAddress = nil
	options.ExcludeSrcPort = nil
	options.ExcludeSrcPortRange = nil
	options.ExcludeDstPort = nil
	options.ExcludeDstPortRange = nil
	options.FileDescriptor = fd
	options.RecvMsgX = false
	options.SendMsgX = false
	options.ProcessorsPerChannel = 1
	if len(options.DNSHijack) == 0 {
		options.DNSHijack = []string{"0.0.0.0:53"}
	}
	return options
}

type androidGlobalState struct {
	tcpConcurrent bool
	tfo, mptcp    bool
	keepIdle      time.Duration
	keepInterval  time.Duration
	disableKeep   bool
	unifiedDelay  bool
	geoMode       bool
	geoLoader     string
	geoMatcher    string
	geoIPURL      string
	geoMMDBURL    string
	geoSiteURL    string
	geoASNURL     string
	userAgent     string
	etag          bool
}

func captureAndroidGlobals() androidGlobalState {
	return androidGlobalState{
		tcpConcurrent: dialer.GetTcpConcurrent(), tfo: inbound.Tfo(), mptcp: inbound.MPTCP(),
		keepIdle: keepalive.KeepAliveIdle(), keepInterval: keepalive.KeepAliveInterval(),
		disableKeep: keepalive.DisableKeepAlive(), unifiedDelay: adapter.UnifiedDelay.Load(),
		geoMode: geodata.GeodataMode(), geoLoader: geodata.LoaderName(), geoMatcher: geodata.SiteMatcherName(),
		geoIPURL: geodata.GeoIpUrl(), geoMMDBURL: geodata.MmdbUrl(), geoSiteURL: geodata.GeoSiteUrl(),
		geoASNURL: geodata.ASNUrl(), userAgent: mihomoHTTP.UA(), etag: resource.ETag(),
	}
}

func (s androidGlobalState) restore() {
	dialer.SetTcpConcurrent(s.tcpConcurrent)
	inbound.SetTfo(s.tfo)
	inbound.SetMPTCP(s.mptcp)
	keepalive.SetKeepAliveIdle(s.keepIdle)
	keepalive.SetKeepAliveInterval(s.keepInterval)
	keepalive.SetDisableKeepAlive(s.disableKeep)
	adapter.UnifiedDelay.Store(s.unifiedDelay)
	geodata.SetGeodataMode(s.geoMode)
	geodata.SetLoader(s.geoLoader)
	geodata.SetSiteMatcher(s.geoMatcher)
	geodata.SetGeoIpUrl(s.geoIPURL)
	geodata.SetMmdbUrl(s.geoMMDBURL)
	geodata.SetGeoSiteUrl(s.geoSiteURL)
	geodata.SetASNUrl(s.geoASNURL)
	mihomoHTTP.SetUA(s.userAgent)
	resource.SetETag(s.etag)
}
