package mihomocore

import (
	"fmt"
	"reflect"
	"regexp"
	"sort"
	"strings"

	"github.com/metacubex/mihomo/adapter/outbound"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
)

// Explicit admission, not a sanitizer: anything not applied by this adapter fails.
// Raw source and graph remain unchanged even when admission fails.
func policy(d *inspection) []issue {
	out := []issue{}
	add := func(path, msg string) { out = append(out, issue{"UNSUPPORTED_CONFIG", msg, path}) }
	for k, v := range d.root {
		switch k {
		case "proxies", "proxy-groups", "proxy-providers", "rules", "mode", "sub-rules", "rule-providers", "hosts":
		case "dns":
			out = append(out, dnsPolicy(v)...)
		case "log-level":
			if v != "silent" {
				add(k, "managed core logging is silent; omit field or use silent")
			}
		case "ipv6", "allow-lan", "geo-auto-update":
			if v != false {
				add(k, "must be false in managed desktop-proxy mode")
			}
		case "port", "socks-port", "mixed-port", "redir-port", "tproxy-port":
			if v != 0 {
				add(k, "source listeners cannot override app-owned loopback listeners; omit or zero")
			}
		case "external-controller", "external-controller-tls", "external-controller-unix", "external-controller-pipe", "external-ui", "external-ui-url", "external-ui-name", "secret":
			if v != "" {
				add(k, "upstream controller/UI disabled; use authenticated app controller")
			}
		case "bind-address":
			if v != "127.0.0.1" && v != "::1" {
				add(k, "only explicit loopback allowed")
			}
		case "find-process-mode":
			if v != "off" {
				add(k, "process lookup not supported in managed mode")
			}
		case "ntp", "iptables", "tun", "profile":
			m, ok := v.(map[string]any)
			if !ok {
				add(k, "mapping required")
				continue
			}
			for field, value := range m {
				allowed := field == "enable"
				if k == "tun" {
					allowed = allowed || field == "auto-route" || field == "auto-redirect" || field == "auto-detect-interface"
				}
				if k == "profile" {
					allowed = field == "store-selected" || field == "store-fake-ip"
				}
				if !allowed || value != false {
					add(k+"."+field, "platform-owned feature unavailable; only explicit disabled flags allowed")
				}
			}
		default:
			add(k, "field is not implemented by managed adapter; preserved, not discarded")
		}
	}
	if rules, ok := d.root["rules"]; ok {
		out = append(out, ruleListPolicy(rules, "rules")...)
	}
	if subs, ok := d.root["sub-rules"]; ok {
		if sub, ok := subs.(map[string]any); ok {
			for k, v := range sub {
				out = append(out, ruleListPolicy(v, "sub-rules."+k)...)
			}
		} else {
			add("sub-rules", "mapping required")
		}
	}
	if providers, ok := d.root["rule-providers"]; ok {
		out = append(out, ruleProviderPolicy(providers)...)
	}
	for i, p := range d.DeclaredGraph.Proxies {
		out = append(out, proxyIssues(p, fmt.Sprintf("proxies[%d]", i))...)
	}
	common := tagFields(reflect.TypeOf(outboundgroup.GroupCommonOption{}), "group")
	for i, g := range d.DeclaredGraph.Groups {
		path := fmt.Sprintf("proxy-groups[%d]", i)
		fields := map[string]reflect.Type{}
		for k, v := range common {
			fields[k] = v
		}
		var specific any
		switch g["type"] {
		case "select":
			specific = outboundgroup.SelectorOption{}
		case "url-test":
			specific = outboundgroup.URLTestOption{}
		case "fallback":
			specific = outboundgroup.FallbackOption{}
		case "load-balance":
			specific = outboundgroup.LoadBalanceOption{}
		default:
			add(path+".type", "unsupported native group type")
		}
		if specific != nil {
			for k, v := range tagFields(reflect.TypeOf(specific), "group") {
				fields[k] = v
			}
		}
		out = append(out, checkFields(g, fields, "group", path)...)
		for _, key := range []string{"filter", "exclude-filter"} {
			if _, err := compileFilters(str(g, key)); err != nil {
				add(path+"."+key, "invalid native regular expression")
			}
		}
		for _, k := range []string{"interval", "timeout", "max-failed-times"} {
			if n, ok := g[k].(int); ok && n < 0 {
				add(path+"."+k, "must not be negative")
			}
		}
	}
	for name, p := range d.DeclaredGraph.Providers {
		path := "proxy-providers." + name
		if name == "default" {
			add(path, "reserved provider name")
		}
		if p["type"] != "http" && p["type"] != "file" && p["type"] != "inline" {
			add(path+".type", "expected http, file or inline")
		}
		for k, v := range p {
			switch k {
			case "type", "url", "path", "payload", "interval", "filter", "exclude-filter", "exclude-type", "health-check":
			default:
				add(path+"."+k, "provider field not implemented; strict update never discards it")
			}
			if k == "interval" {
				if n, ok := v.(int); !ok || n < 0 || n > 86400*30 {
					add(path+"."+k, "expected integer 0..2592000")
				}
			}
		}
		if hc, exists := p["health-check"]; exists {
			m, ok := hc.(map[string]any)
			if !ok {
				add(path+".health-check", "mapping required")
			} else {
				for k, v := range m {
					switch k {
					case "enable", "lazy":
						if _, ok := v.(bool); !ok {
							add(path+".health-check."+k, "boolean required")
						}
					case "url", "expected-status":
						if _, ok := v.(string); !ok {
							add(path+".health-check."+k, "string required")
						}
					case "interval", "timeout":
						if n, ok := v.(int); !ok || n < 0 || n > 86400*30 {
							add(path+".health-check."+k, "invalid interval/timeout")
						}
					default:
						add(path+".health-check."+k, "unknown health-check field")
					}
				}
			}
		}
		if payload, exists := p["payload"]; exists {
			list, ok := payload.([]any)
			if !ok {
				add(path+".payload", "sequence required")
			} else {
				for i, v := range list {
					m, ok := v.(map[string]any)
					if !ok {
						add(path+".payload", "proxy mapping required")
						continue
					}
					out = append(out, proxyIssues(m, fmt.Sprintf("%s.payload[%d]", path, i))...)
				}
			}
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Path < out[j].Path })
	return out
}

func tagFields(t reflect.Type, tag string) map[string]reflect.Type {
	fields := map[string]reflect.Type{}
	for i := 0; i < t.NumField(); i++ {
		f := t.Field(i)
		if f.Anonymous && f.Type.Kind() == reflect.Struct {
			for k, v := range tagFields(f.Type, tag) {
				fields[k] = v
			}
		}
		key := strings.Split(f.Tag.Get(tag), ",")[0]
		if key != "" && key != "-" {
			fields[key] = f.Type
		}
	}
	return fields
}
func checkFields(m map[string]any, fields map[string]reflect.Type, tag, path string) []issue {
	return checkFieldsMode(m, fields, tag, path, false)
}

// Mihomo has deliberately open-ended option maps (for example Shadowsocks
// plugin-opts). Keep strict checking for typed fields while delegating those
// maps to the pinned upstream decoder instead of rejecting valid protocols.
func checkMihomoFields(m map[string]any, fields map[string]reflect.Type, tag, path string) []issue {
	return checkFieldsMode(m, fields, tag, path, true)
}

func checkFieldsMode(m map[string]any, fields map[string]reflect.Type, tag, path string, allowOpaqueMaps bool) []issue {
	out := []issue{}
	for k, v := range m {
		t, ok := fields[k]
		if !ok {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "unknown field is preserved but cannot run", path + "." + k})
			continue
		}
		for t.Kind() == reflect.Pointer {
			t = t.Elem()
		}
		if t.Kind() == reflect.Struct {
			if child, ok := v.(map[string]any); ok {
				out = append(out, checkFieldsMode(child, tagFields(t, tag), tag, path+"."+k, allowOpaqueMaps)...)
			}
		}
		if !allowOpaqueMaps && t.Kind() == reflect.Map && t.Elem().Kind() == reflect.Interface {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "opaque options need an explicit schema before native use", path + "." + k})
		}
	}
	return out
}
func proxyIssues(p map[string]any, path string) []issue {
	var option any
	switch p["type"] {
	case "direct":
		option = outbound.DirectOption{}
	case "reject":
		option = outbound.RejectOption{}
	case "ss":
		option = outbound.ShadowSocksOption{}
	case "socks5":
		option = outbound.Socks5Option{}
	case "http":
		option = outbound.HttpOption{}
	case "vmess":
		option = outbound.VmessOption{}
	case "vless":
		option = outbound.VlessOption{}
	case "trojan":
		option = outbound.TrojanOption{}
	case "hysteria2":
		option = outbound.Hysteria2Option{}
	case "tuic":
		option = outbound.TuicOption{}
	case "wireguard":
		option = outbound.WireGuardOption{}
	case "mieru":
		option = outbound.MieruOption{}
	case "anytls":
		option = outbound.AnyTLSOption{}
	default:
		return []issue{{"UNSUPPORTED_CONFIG", "proxy protocol not yet admitted by managed adapter", path + ".type"}}
	}
	fields := tagFields(reflect.TypeOf(option), "proxy")
	fields["type"] = reflect.TypeOf("")
	out := checkFields(p, fields, "proxy", path)
	for _, k := range []string{"interface-name", "routing-mark", "dialer-proxy"} {
		if _, ok := p[k]; ok {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "requires platform/dialer graph ownership", path + "." + k})
		}
	}
	if name, ok := p["name"].(string); !ok || name == "" {
		out = append(out, issue{"INVALID_CONFIG", "nonempty proxy name required", path + ".name"})
	}
	return out
}

// mihomoProxyIssues validates against the pinned upstream protocol option types
// for Android provider payloads. The older managed desktop/mobile admission
// remains deliberately narrower; Android's upstream runtime does not share it.
func mihomoProxyIssues(p map[string]any, path string) []issue {
	var option any
	switch p["type"] {
	case "direct":
		option = outbound.DirectOption{}
	case "reject":
		option = outbound.RejectOption{}
	case "rematch":
		option = outbound.RematchOption{}
	case "dns":
		option = outbound.DnsOption{}
	case "ss":
		option = outbound.ShadowSocksOption{}
	case "ssr":
		option = outbound.ShadowSocksROption{}
	case "socks5":
		option = outbound.Socks5Option{}
	case "http":
		option = outbound.HttpOption{}
	case "vmess":
		option = outbound.VmessOption{}
	case "vless":
		option = outbound.VlessOption{}
	case "snell":
		option = outbound.SnellOption{}
	case "trojan":
		option = outbound.TrojanOption{}
	case "hysteria":
		option = outbound.HysteriaOption{}
	case "hysteria2":
		option = outbound.Hysteria2Option{}
	case "wireguard":
		option = outbound.WireGuardOption{}
	case "tuic":
		option = outbound.TuicOption{}
	case "shadowquic":
		option = outbound.ShadowQuicOption{}
	case "gost-relay":
		option = outbound.GostRelayOption{}
	case "ssh":
		option = outbound.SshOption{}
	case "mieru":
		option = outbound.MieruOption{}
	case "anytls":
		option = outbound.AnyTLSOption{}
	case "sudoku":
		option = outbound.SudokuOption{}
	case "masque":
		option = outbound.MasqueOption{}
	case "trusttunnel":
		option = outbound.TrustTunnelOption{}
	case "openvpn":
		option = outbound.OpenVPNOption{}
	case "tailscale", "zerotier", "easytier":
		return []issue{{"UNSUPPORTED_CONFIG", "mesh protocol removed from Nimbo build", path + ".type"}}
	default:
		return []issue{{"UNSUPPORTED_CONFIG", "proxy protocol is not supported by the pinned Mihomo parser", path + ".type"}}
	}
	fields := tagFields(reflect.TypeOf(option), "proxy")
	fields["type"] = reflect.TypeOf("")
	// Generators commonly annotate DIRECT with udp: true. Direct intrinsically
	// supports UDP; udp: false would require a semantic override we do not invent.
	if (p["type"] == "direct" || p["type"] == "hysteria2") && p["udp"] == true {
		fields["udp"] = reflect.TypeOf(true)
	}

	// Generator annotations that describe intrinsic transport properties.
	if p["type"] == "ss" && p["network"] == "tcp" {
		fields["network"] = reflect.TypeOf("")
	}
	if (p["type"] == "trojan" || p["type"] == "anytls") && p["tls"] == true {
		fields["tls"] = reflect.TypeOf(true)
	}
	// uTLS fingerprints do not apply to Hysteria2's QUIC TLS implementation.
	if p["type"] == "hysteria2" {
		fields["client-fingerprint"] = reflect.TypeOf("")
	}
	checked := p
	if options, ok := p["xhttp-opts"].(map[string]any); ok {
		if serverSecs, exists := options["sc-stream-up-server-secs"]; exists {
			// This is a SERVER lifetime range, not a client transport setting.
			value, ok := serverSecs.(string)
			if !ok || !regexp.MustCompile(`^[0-9]+(-[0-9]+)?$`).MatchString(value) {
				return []issue{{"INVALID_CONFIG", "server lifetime range required", path + ".xhttp-opts.sc-stream-up-server-secs"}}
			}
			checked = make(map[string]any, len(p))
			for k, v := range p {
				checked[k] = v
			}
			clientOptions := make(map[string]any, len(options))
			for k, v := range options {
				if k != "sc-stream-up-server-secs" {
					clientOptions[k] = v
				}
			}
			checked["xhttp-opts"] = clientOptions
		}
	}

	return checkMihomoFields(checked, fields, "proxy", path)
}
