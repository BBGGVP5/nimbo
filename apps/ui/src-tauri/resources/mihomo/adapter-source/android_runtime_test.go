package mihomocore

import (
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/component/geodata"
	C "github.com/metacubex/mihomo/constant"
)

func TestAndroidNativeParseAndTunProjection(t *testing.T) {
	stopTest(t)
	source := `mode: rule
ipv6: true
dns:
  enable: true
  default-nameserver: [1.1.1.1]
  nameserver: [https://1.1.1.1/dns-query]
proxies: [{name: local, type: direct}]
proxy-groups: [{name: GLOBAL, type: select, proxies: [local]}]
rules: ["MATCH,GLOBAL"]
`
	d, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	d.android = true
	if err := androidRuntimePolicy(d); err != nil {
		t.Fatal(err)
	}
	cfg, err := nativeParseAndroid(d, true)
	if err != nil {
		t.Fatal(err)
	}
	defer closeConfig(cfg)
	if !cfg.General.IPv6 || !cfg.DNS.Enable || !cfg.General.GeodataMode {
		t.Fatalf("native Mihomo config lost Android feature choices: ipv6=%v dns=%v geodata=%v", cfg.General.IPv6, cfg.DNS.Enable, cfg.General.GeodataMode)
	}
	plan := androidTunProjection(cfg.General.Tun, 42, true)
	if !plan.Enable || plan.FileDescriptor != 42 || plan.AutoRoute || plan.AutoRedirect ||
		!plan.Inet4Address[0].IsValid() || len(plan.Inet6Address) != 1 || plan.Stack != C.TunGvisor {
		t.Fatalf("Android must own interface/routes while retaining upstream gVisor: %+v", plan)
	}
	if !strings.Contains(strings.Join(plan.DNSHijack, ","), ":53") {
		t.Fatalf("DNS requests must enter the core resolver: %v", plan.DNSHijack)
	}
}

func TestAndroidUpstreamGroupsAndInlineProvidersRemainNative(t *testing.T) {
	stopTest(t)
	source := `mode: rule
dns:
  enable: true
  default-nameserver: [1.1.1.1]
  nameserver: [https://1.1.1.1/dns-query]
proxy-providers:
  edge:
    type: inline
    override: {additional-prefix: 'feed:', udp: true}
    payload:
      - {name: edge-one, type: socks5, server: 192.0.2.10, port: 1080, udp: true}
proxy-groups:
  - {name: Auto, type: url-test, use: [edge], url: https://1.1.1.1/generate_204, interval: 300}
rules: ["MATCH,Auto"]
`
	d, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	d.android = true
	if err := androidRuntimePolicy(d); err != nil {
		t.Fatal(err)
	}
	cfg, err := nativeParseAndroid(d, false)
	if err != nil {
		t.Fatal(err)
	}
	defer closeConfig(cfg)
	if err := rebindProviders(cfg, d, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	if err := rebindRuleProviders(cfg, d, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	group, ok := cfg.Proxies["Auto"]
	if !ok {
		t.Fatal("native automatic group missing after provider rebinding")
	}
	if _, ok := group.Adapter().(outboundgroup.ProxyGroup); !ok {
		t.Fatalf("automatic group was replaced by a non-native adapter: %T", group.Adapter())
	}
	provider := cfg.Providers["edge"]
	if provider == nil || len(provider.Proxies()) != 1 || provider.Proxies()[0].Name() != "feed:edge-one" {
		t.Fatalf("inline provider membership was not retained: %#v", provider)
	}
}

func TestAndroidRuleProviderRetainsProcessRulesAtNativeParseBoundary(t *testing.T) {
	_, err := parseAndroidRule("PROCESS-NAME", "app", "GLOBAL", nil, nil)
	if err != nil {
		t.Fatal("Android flow owner supports native process rules")
	}
	if _, err := parseAndroidRule("DOMAIN-SUFFIX", "example.com", "GLOBAL", nil, nil); err != nil {
		t.Fatalf("normal upstream rule was rejected: %v", err)
	}
}

func TestMihomoProxySchemaAcceptsOpenPluginOptions(t *testing.T) {
	proxy := map[string]any{
		"name": "plugin-node", "type": "ss", "server": "192.0.2.10", "port": 443,
		"cipher": "aes-128-gcm", "password": "secret", "plugin": "obfs-local",
		"plugin-opts": map[string]any{"mode": "tls", "host": "example.com"},
	}
	if issues := mihomoProxyIssues(proxy, "proxies[0]"); len(issues) != 0 {
		t.Fatalf("valid upstream open-ended plugin options were rejected: %+v", issues)
	}
}

func TestAndroidBundledGeoAssetsParseWithPinnedMihomo(t *testing.T) {
	stopTest(t)
	oldHome := C.Path.HomeDir()
	home := t.TempDir()
	C.SetHomeDir(home)
	defer C.SetHomeDir(oldHome)
	assets := filepath.Join("..", "..", "..", "app", "src", "main", "assets")
	for source, target := range map[string]string{"geoip.dat": "GeoIP.dat", "geosite.dat": "GeoSite.dat"} {
		in, err := os.Open(filepath.Join(assets, source))
		if err != nil {
			t.Fatalf("open bundled %s: %v", source, err)
		}
		out, err := os.Create(filepath.Join(home, target))
		if err != nil {
			in.Close()
			t.Fatalf("create Mihomo %s: %v", target, err)
		}
		_, copyErr := io.Copy(out, in)
		closeOutErr := out.Close()
		closeInErr := in.Close()
		if copyErr != nil || closeOutErr != nil || closeInErr != nil {
			t.Fatalf("copy bundled %s: copy=%v closeOutput=%v closeInput=%v", source, copyErr, closeOutErr, closeInErr)
		}
	}
	geodata.ClearGeoSiteCache()
	geodata.ClearGeoIPCache()
	t.Cleanup(func() {
		geodata.ClearGeoSiteCache()
		geodata.ClearGeoIPCache()
	})
	if _, err := geodata.LoadGeoSiteMatcher("cn"); err != nil {
		t.Fatalf("bundled Xray GeoSite data is not readable by pinned Mihomo: %v", err)
	}
	if _, err := geodata.LoadGeoIPMatcher("cn"); err != nil {
		t.Fatalf("bundled Xray GeoIP data is not readable by pinned Mihomo: %v", err)
	}
}
