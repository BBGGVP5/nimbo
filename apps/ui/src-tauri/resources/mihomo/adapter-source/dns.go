package mihomocore

import (
	"reflect"
	"strings"

	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/dns"
)

func dnsPolicy(value any) []issue {
	m, ok := value.(map[string]any)
	if !ok {
		return []issue{{"INVALID_CONFIG", "mapping required", "dns"}}
	}
	out := checkFields(m, tagFields(reflect.TypeOf(config.RawDNS{}), "yaml"), "yaml", "dns")
	for k, v := range m {
		if strings.HasPrefix(k, "fake-ip-") {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "fake-IP requires mobile network plan", "dns." + k})
		}
		switch k {
		case "ipv6":
			if v != false {
				out = append(out, issue{"UNSUPPORTED_CONFIG", "IPv6 requires validated platform mode", "dns.ipv6"})
			}
		case "listen":
			if v != "" {
				out = append(out, issue{"UNSUPPORTED_CONFIG", "DNS is internal; no host listener in desktop mode", "dns.listen"})
			}
		case "enhanced-mode":
			if v != "redir-host" && v != "normal" {
				out = append(out, issue{"UNSUPPORTED_CONFIG", "fake-ip requires matching TUN DNS ownership", "dns.enhanced-mode"})
			}
		case "listen-routing-mark":
			if v != 0 {
				out = append(out, issue{"UNSUPPORTED_CONFIG", "host routing mark unavailable", "dns.listen-routing-mark"})
			}
		case "nameserver-policy":
			out = append(out, issue{"UNSUPPORTED_CONFIG", "DNS rule-provider policy binding not yet integrated", "dns.nameserver-policy"})
		case "fallback-filter":
			if filter, ok := v.(map[string]any); ok {
				if filter["geoip"] == true || filter["geosite"] != nil {
					out = append(out, issue{"UNSUPPORTED_CONFIG", "fallback geodata needs explicit assets", "dns.fallback-filter"})
				}
			}
		}
		if text, ok := v.(string); ok && strings.Contains(text, "geosite:") {
			out = append(out, issue{"UNSUPPORTED_CONFIG", "geodata requires explicit assets", "dns." + k})
		}
	}
	return out
}

type dnsState struct {
	defaultResolver, proxy, direct resolver.Resolver
	defaultMapper                  resolver.Enhancer
	defaultService                 resolver.Service
	hosts                          resolver.Hosts
	systemHosts                    bool
}

func saveDNS() dnsState {
	return dnsState{
		defaultResolver: resolver.DefaultResolver, proxy: resolver.ProxyServerHostResolver,
		direct: resolver.DirectHostResolver, defaultMapper: resolver.DefaultHostMapper,
		defaultService: resolver.DefaultService, hosts: resolver.DefaultHosts,
		systemHosts: resolver.UseSystemHosts,
	}
}
func (s dnsState) restore() {
	if resolver.DefaultResolver != nil {
		resolver.DefaultResolver.ResetConnection()
	}
	resolver.DefaultResolver = s.defaultResolver
	resolver.DefaultHostMapper = s.defaultMapper
	resolver.DefaultService = s.defaultService
	resolver.ProxyServerHostResolver = s.proxy
	resolver.DirectHostResolver = s.direct
	resolver.DefaultHosts = s.hosts
	resolver.UseSystemHosts = s.systemHosts
}
func applyInternalDNS(cfg *config.Config) {
	resolver.DefaultHosts = resolver.NewHosts(cfg.Hosts)
	c := cfg.DNS
	if !c.Enable {
		return
	}
	// Resolver only: never dns.ReCreateServer, system DNS settings or fake-IP pool.
	r := dns.NewResolver(dns.Config{Main: c.NameServer, Fallback: c.Fallback, IPv6: false, IPv6Timeout: c.IPv6Timeout, FallbackIPFilter: c.FallbackIPFilter, FallbackDomainFilter: c.FallbackDomainFilter, FallbackLazyQuery: c.FallbackLazyQuery, Default: c.DefaultNameserver, Policy: c.NameServerPolicy, ProxyServer: c.ProxyServerNameserver, ProxyServerPolicy: c.ProxyServerPolicy, DirectServer: c.DirectNameServer, DirectFollowPolicy: c.DirectFollowPolicy, CacheAlgorithm: c.CacheAlgorithm, CacheMaxSize: c.CacheMaxSize})
	resolver.DefaultResolver = r
	resolver.UseSystemHosts = c.UseSystemHosts
	if r.ProxyResolver.Invalid() {
		resolver.ProxyServerHostResolver = r.ProxyResolver
	} else {
		resolver.ProxyServerHostResolver = r.Resolver
	}
	if r.DirectResolver.Invalid() {
		resolver.DirectHostResolver = r.DirectResolver
	} else {
		resolver.DirectHostResolver = r.Resolver
	}
}
