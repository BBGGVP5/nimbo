package mihomocore

import (
	"context"
	"crypto/sha256"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/dlclark/regexp2"
	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	AP "github.com/metacubex/mihomo/adapter/provider"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/tunnel"
)

func str(m map[string]any, k string) string  { v, _ := m[k].(string); return v }
func integer(m map[string]any, k string) int { v, _ := m[k].(int); return v }
func payload(m map[string]any) []map[string]any {
	out := []map[string]any{}
	list, _ := m["payload"].([]any)
	for _, v := range list {
		if p, ok := v.(map[string]any); ok {
			out = append(out, p)
		}
	}
	return out
}

// Custom vehicle is bounded, has no environment proxy and writes only in the
// app-owned directory. The native Fetcher parses before publishing membership.
type managedVehicle struct {
	kind      P.VehicleType
	path, url string
}

func (v *managedVehicle) Type() P.VehicleType { return v.kind }
func (v *managedVehicle) Path() string        { return v.path }
func (v *managedVehicle) Url() string         { return v.url }
func (v *managedVehicle) Proxy() string       { return "" }
func (v *managedVehicle) Read(ctx context.Context, _ utils.HashType) ([]byte, utils.HashType, error) {
	var reader io.ReadCloser
	if v.kind == P.File {
		f, err := os.Open(v.path)
		if err != nil {
			return nil, utils.HashType{}, err
		}
		reader = f
	} else {
		ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
		defer cancel()
		transport := &http.Transport{Proxy: nil, DialContext: func(c context.Context, n, a string) (net.Conn, error) { return dialer.DialContext(c, n, a) }, TLSHandshakeTimeout: 5 * time.Second, ResponseHeaderTimeout: 10 * time.Second}
		defer transport.CloseIdleConnections()
		client := &http.Client{Transport: transport, CheckRedirect: func(r *http.Request, via []*http.Request) error {
			if len(via) >= 5 {
				return fmt.Errorf("too many redirects")
			}
			return checkURL(r.URL.String())
		}}
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, v.url, nil)
		if err != nil {
			return nil, utils.HashType{}, err
		}
		resp, err := client.Do(req)
		if err != nil {
			return nil, utils.HashType{}, err
		}
		reader = resp.Body
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			reader.Close()
			return nil, utils.HashType{}, fmt.Errorf("provider HTTP status %d", resp.StatusCode)
		}
	}
	defer reader.Close()
	b, err := io.ReadAll(io.LimitReader(reader, maxSource+1))
	if err == nil && len(b) > maxSource {
		err = fmt.Errorf("provider exceeds 4 MiB")
	}
	return b, utils.MakeHash(b), err
}
func (v *managedVehicle) Write(b []byte) error {
	if v.kind == P.File {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(v.path), 0700); err != nil {
		return err
	}
	f, err := os.CreateTemp(filepath.Dir(v.path), "provider-*.tmp")
	if err != nil {
		return err
	}
	name := f.Name()
	defer os.Remove(name)
	if _, err = f.Write(b); err != nil {
		f.Close()
		return err
	}
	if err = f.Close(); err != nil {
		return err
	}
	// Windows cannot replace an existing destination using every rename strategy;
	// native membership remains unchanged if the atomic cache rename fails.
	return os.Rename(name, v.path)
}
func checkURL(s string) error {
	u, err := url.Parse(s)
	if err != nil || u.Hostname() == "" || (u.Scheme != "http" && u.Scheme != "https") || u.User != nil {
		return fmt.Errorf("explicit HTTP(S) URL without userinfo required")
	}
	return nil
}
func safeProviderPath(home, path string) (string, error) {
	if !filepath.IsLocal(path) {
		return "", fmt.Errorf("provider path must be relative to app dataDir")
	}
	full := filepath.Join(home, path)
	// Refuse existing symlinks/reparse symlinks in any component. dataDir ownership
	// is a platform responsibility; a hostile same-user filesystem is out of scope.
	rel, _ := filepath.Rel(home, full)
	cur := home
	for _, part := range strings.Split(rel, string(os.PathSeparator)) {
		cur = filepath.Join(cur, part)
		fi, err := os.Lstat(cur)
		if err == nil && fi.Mode()&os.ModeSymlink != 0 {
			return "", fmt.Errorf("provider symlink forbidden")
		}
		if err != nil && !os.IsNotExist(err) {
			return "", err
		}
	}
	if fi, err := os.Stat(full); err == nil && fi.Size() > maxSource {
		return "", fmt.Errorf("provider cache exceeds 4 MiB")
	}
	return full, nil
}

func compileFilters(s string) ([]*regexp2.Regexp, error) {
	var out []*regexp2.Regexp
	if s == "" {
		return out, nil
	}
	for _, part := range strings.Split(s, "`") {
		r, err := regexp2.Compile(part, regexp2.None)
		if err != nil {
			return nil, err
		}
		r.MatchTimeout = 100 * time.Millisecond
		out = append(out, r)
	}
	return out, nil
}
func matches(rs []*regexp2.Regexp, s string) (bool, error) {
	for _, r := range rs {
		ok, err := r.MatchString(s)
		if err != nil {
			return false, err
		}
		if ok {
			return true, nil
		}
	}
	return false, nil
}
func strictProxyParser(name string, m map[string]any, extra ...func(map[string]any, string) error) (func([]byte) ([]C.Proxy, error), error) {
	return strictProxyParserWith(name, m, proxyIssues, extra...)
}

func strictMihomoProxyParser(name string, m map[string]any, extra ...func(map[string]any, string) error) (func([]byte) ([]C.Proxy, error), error) {
	checks := []func(map[string]any, string) error{func(proxy map[string]any, at string) error {
		if _, exists := proxy["interface-name"]; exists {
			return problem("UNSUPPORTED_ANDROID_CONFIG", at+".interface-name", "physical interface binding belongs to Android Network")
		}
		if _, exists := proxy["routing-mark"]; exists {
			return problem("UNSUPPORTED_ANDROID_CONFIG", at+".routing-mark", "host routing marks are not available inside Android VpnService")
		}
		return nil
	}}
	checks = append(checks, extra...)
	return strictProxyParserWith(name, m, mihomoProxyIssues, checks...)
}

func strictProxyParserWith(name string, m map[string]any, validate func(map[string]any, string) []issue, extra ...func(map[string]any, string) error) (func([]byte) ([]C.Proxy, error), error) {
	include, err := compileFilters(str(m, "filter"))
	if err != nil {
		return nil, err
	}
	exclude, err := compileFilters(str(m, "exclude-filter"))
	if err != nil {
		return nil, err
	}
	return func(b []byte) (result []C.Proxy, err error) {
		// No fallback conversion, silently skipped malformed rows, or duplicate names.
		doc, e := decodeDocument(string(b))
		if e != nil {
			return nil, e
		}
		if len(doc) != 1 || doc["proxies"] == nil {
			return nil, fmt.Errorf("provider YAML must contain only proxies")
		}
		list, ok := doc["proxies"].([]any)
		if !ok {
			return nil, fmt.Errorf("provider proxies sequence required")
		}
		seen := map[string]bool{}
		filterOrder := map[string]int{}
		defer func() {
			if err != nil {
				for _, p := range result {
					_ = p.Close()
				}
				result = nil
			}
		}()
		for i, v := range list {
			p, ok := v.(map[string]any)
			if !ok {
				return result, fmt.Errorf("provider row %d is not mapping", i)
			}
			if issues := validate(p, fmt.Sprintf("provider.%s[%d]", name, i)); len(issues) > 0 {
				return result, &issues[0]
			}
			for _, check := range extra {
				if err := check(p, fmt.Sprintf("provider.%s[%d]", name, i)); err != nil {
					return result, err
				}
			}
			pn := str(p, "name")
			if seen[pn] {
				return result, fmt.Errorf("duplicate provider proxy name")
			}
			seen[pn] = true
			drop := false
			for _, typ := range strings.Split(str(m, "exclude-type"), "|") {
				if strings.EqualFold(typ, str(p, "type")) {
					drop = true
				}
			}
			match, e := matches(exclude, pn)
			if e != nil {
				return result, e
			}
			if match {
				drop = true
			}
			if len(include) > 0 {
				match = false
				for index, regex := range include {
					hit, err := regex.MatchString(pn)
					if err != nil {
						return result, err
					}
					if hit {
						match = true
						filterOrder[pn] = index
						break
					}
				}
				if !match {
					drop = true
				}
			}
			if drop {
				continue
			}
			proxy, e := adapter.ParseProxy(p, adapter.WithTunnelForAPI(tunnel.Tunnel), adapter.WithProviderName(name))
			if e != nil {
				return result, e
			}
			result = append(result, proxy)
		}
		if len(result) == 0 {
			return result, fmt.Errorf("provider has no matching valid proxy")
		}
		// Upstream iterates filter alternatives first, then source rows. Preserve
		// this order: it affects the initial selection in dependent native groups.
		if len(include) > 1 {
			sort.SliceStable(result, func(i, j int) bool { return filterOrder[result[i].Name()] < filterOrder[result[j].Name()] })
		}
		return result, nil
	}, nil
}

func managedProvider(name string, m map[string]any, home string, extra ...func(map[string]any, string) error) (P.ProxyProvider, error) {
	return managedProviderWithParser(name, m, home, strictProxyParser, extra...)
}

func managedMihomoProvider(name string, m map[string]any, home string, extra ...func(map[string]any, string) error) (P.ProxyProvider, error) {
	return managedProviderWithParser(name, m, home, strictMihomoProxyParser, extra...)
}

func managedProviderWithParser(name string, m map[string]any, home string,
	parserFactory func(string, map[string]any, ...func(map[string]any, string) error) (func([]byte) ([]C.Proxy, error), error),
	extra ...func(map[string]any, string) error,
) (P.ProxyProvider, error) {
	parser, err := parserFactory(name, m, extra...)
	if err != nil {
		return nil, err
	}
	hcMap, _ := m["health-check"].(map[string]any)
	interval := integer(hcMap, "interval")
	if hcMap["enable"] != true {
		interval = 0
	} else if interval == 0 {
		interval = 300
	}
	expected, err := utils.NewUnsignedRanges[uint16](str(hcMap, "expected-status"))
	if err != nil {
		return nil, err
	}
	hc := AP.NewHealthCheck(nil, str(hcMap, "url"), uint(integer(hcMap, "timeout")), uint(interval), hcMap["lazy"] != false, expected)
	if m["type"] == "inline" {
		return AP.NewInlineProvider(name, payload(m), parser, hc)
	}
	vehicle := &managedVehicle{kind: P.File}
	if m["type"] == "http" {
		vehicle.kind = P.HTTP
		vehicle.url = str(m, "url")
		if err := checkURL(vehicle.url); err != nil {
			return nil, err
		}
	}
	path := str(m, "path")
	if path == "" && vehicle.kind == P.HTTP {
		path = fmt.Sprintf("providers/%x.yaml", sha256.Sum256([]byte(name+"\x00"+vehicle.url)))
	}
	vehicle.path, err = safeProviderPath(home, path)
	if err != nil {
		return nil, err
	}
	return AP.NewProxySetProvider(name, time.Duration(integer(m, "interval"))*time.Second, payload(m), parser, vehicle, hc)
}

// Rebind groups to strict native providers, retaining dynamic use/include-all.
// The original config was already validated by the pinned parser. No YAML export
// is ever reconstructed from this runtime graph.
func rebindProviders(cfg *config.Config, d *inspection, home string) error {
	if d.android || d.desktop {
		// ParseRawConfig already built the complete, pinned Mihomo provider and
		// group graph. Keep those upstream implementations on Android so provider
		// proxy/header/size-limit/age/override options, auto groups and refresh
		// callbacks retain their native semantics. The Android admission layer
		// validates platform-owned routing fields before this point.
		return nil
	}
	for _, p := range cfg.Providers {
		closeProvider(p)
	}
	cfg.Providers = map[string]P.ProxyProvider{}
	for name, p := range cfg.Proxies {
		if _, ok := p.Adapter().(outboundgroup.ProxyGroup); ok {
			_ = p.Close()
			delete(cfg.Proxies, name)
		}
	}
	allProxies := []string{}
	for _, p := range d.DeclaredGraph.Proxies {
		allProxies = append(allProxies, str(p, "name"))
	}
	declaredProxyOrder := append([]string{}, allProxies...)
	sort.Strings(allProxies) // upstream include-all-proxies uses lexical order
	allProviders := []string{}
	for name := range d.DeclaredGraph.Providers {
		allProviders = append(allProviders, name)
	}
	sort.Strings(allProviders)
	for _, name := range allProviders {
		var checks []func(map[string]any, string) error
		if d.mobile {
			checks = append(checks, mobileProxyPolicy)
			if mobileAutomaticProviderUsed(d, name) {
				checks = append(checks, func(proxy map[string]any, at string) error {
					if str(proxy, "type") == "direct" {
						return problem("UNSUPPORTED_MOBILE_CONFIG", at+".type", "providers used by automatic groups may not contain DIRECT")
					}
					return nil
				})
			}
		}
		var (
			p   P.ProxyProvider
			err error
		)
		if d.android {
			p, err = managedMihomoProvider(name, d.DeclaredGraph.Providers[name], home)
		} else {
			p, err = managedProvider(name, d.DeclaredGraph.Providers[name], home, checks...)
		}
		if err != nil {
			return err
		}
		cfg.Providers[name] = p
	}
	groups := map[string]map[string]any{}
	for _, g := range d.DeclaredGraph.Groups {
		groups[str(g, "name")] = g
	}
	state := map[string]int{}
	var build func(string) error
	build = func(name string) error {
		if state[name] == 2 {
			return nil
		}
		if state[name] == 1 {
			return fmt.Errorf("group cycle")
		}
		state[name] = 1
		g := groups[name]
		refs, _ := g["proxies"].([]any)
		for _, v := range refs {
			ref, _ := v.(string)
			if _, ok := groups[ref]; ok {
				if err := build(ref); err != nil {
					return err
				}
			}
		}
		p, err := outboundgroup.ParseProxyGroup(g, cfg.Proxies, cfg.Providers, allProxies, allProviders)
		if err != nil {
			return err
		}
		cfg.Proxies[name] = adapter.NewProxy(p)
		state[name] = 2
		return nil
	}
	for _, g := range d.DeclaredGraph.Groups {
		if err := build(str(g, "name")); err != nil {
			return err
		}
	}
	// Match upstream default/GLOBAL provider ordering (builtins, proxies, groups).
	ps := []C.Proxy{cfg.Proxies["DIRECT"], cfg.Proxies["REJECT"]}
	for _, n := range declaredProxyOrder {
		ps = append(ps, cfg.Proxies[n])
	}
	for _, g := range d.DeclaredGraph.Groups {
		ps = append(ps, cfg.Proxies[str(g, "name")])
	}
	hc := AP.NewHealthCheck(ps, "", 5000, 0, true, nil)
	pd, err := AP.NewCompatibleProvider("default", ps, hc)
	if err != nil {
		return err
	}
	cfg.Providers["default"] = pd
	if _, ok := cfg.Proxies["GLOBAL"]; !ok {
		g, err := outboundgroup.NewSelector(outboundgroup.GroupCommonOption{Name: "GLOBAL"}, outboundgroup.SelectorOption{}, cfg.Proxies["COMPATIBLE"], []P.ProxyProvider{pd})
		if err != nil {
			return err
		}
		cfg.Proxies["GLOBAL"] = adapter.NewProxy(g)
	}
	return nil
}
func closeProvider(p any) {
	if c, ok := p.(io.Closer); ok {
		_ = c.Close()
	}
}
func closeConfig(cfg *config.Config) {
	if cfg == nil {
		return
	}
	for _, p := range cfg.Providers {
		closeProvider(p)
	}
	for _, p := range cfg.RuleProviders {
		closeProvider(p)
	}
	for _, p := range cfg.Proxies {
		_ = p.Close()
	}
}
