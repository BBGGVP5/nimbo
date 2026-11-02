package mihomocore

import (
	C "github.com/metacubex/mihomo/constant"
	"net/netip"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestAndroidProviderClientSettings(t *testing.T) {
	source := strings.Replace(mobileConfig, "mode: global", "mode: rule", 1) + `
mixed-port: 7890
allow-lan: true
bind-address: '*'
lan-allowed-ips: [0.0.0.0/0]
enable-process: true
find-process-mode: always
log-level: info
profile: {store-selected: true, store-fake-ip: true}
tun: {enable: true, stack: gvisor, auto-route: true, auto-detect-interface: true, strict-route: true, exclude-package: [org.example.app]}
rules: ['PROCESS-NAME-REGEX,(?i).*telegram.*,GLOBAL', 'MATCH,GLOBAL']
`
	d, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	if err = androidRuntimePolicy(d); err != nil {
		t.Fatal(err)
	}
	if d.OriginalYAML != source {
		t.Fatal("source changed")
	}
	cfg, err := nativeParseAndroid(d, false)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.General.MixedPort != 0 || cfg.General.AllowLan {
		t.Fatal("source host listener escaped Android projection")
	}
	if len(cfg.Rules) != 2 {
		t.Fatal("process routing rule discarded")
	}
}

// Optional local diagnostic: the confidential provider source never enters fixtures or logs.
func TestAndroidPrivateProviderAdmission(t *testing.T) {
	path := os.Getenv("NIMBO_PRIVATE_PROVIDER")
	if path == "" {
		t.Skip("no private provider fixture")
	}
	source, err := os.ReadFile(path)
	if err != nil {
		t.Fatal("cannot read local provider")
	}
	d, err := inspect(string(source))
	if err != nil {
		t.Fatal("provider inspect failed")
	}

	if err = androidRuntimePolicy(d); err != nil {
		if p, ok := err.(*issue); ok {
			t.Fatalf("admission: %s at %s", p.Code, p.Path)
		}
		t.Fatal("provider admission failed")
	}
	stopTest(t)
	oldHome := C.Path.HomeDir()
	home := t.TempDir()
	C.SetHomeDir(home)
	defer C.SetHomeDir(oldHome)
	for source, target := range map[string]string{"geoip.dat": "GeoIP.dat", "geosite.dat": "GeoSite.dat"} {
		data, err := os.ReadFile(filepath.Join("..", "..", "..", "app", "src", "main", "assets", source))
		if err != nil {
			t.Fatal("bundled geodata missing")
		}
		if os.WriteFile(filepath.Join(home, target), data, 0600) != nil {
			t.Fatal("cannot prepare isolated geodata")
		}
	}
	if err = openSessionCache(); err != nil {
		t.Fatal("cannot open isolated cache")
	}
	defer closeSessionCache()
	cfg, err := nativeParseAndroid(d, false, []string{"192.0.2.53"})
	if err != nil {
		if p, ok := err.(*issue); ok {
			t.Fatalf("native parse: %s at %s (private detail withheld)", p.Code, p.Path)
		}
		t.Fatal("native parse failed (private detail withheld)")
	}
	defer closeConfig(cfg)
	if len(cfg.Rules) == 0 || len(cfg.Proxies) < 80 {
		t.Fatal("native parser dropped provider graph")
	}

}

type testFlowOwner struct{}

func (testFlowOwner) Resolve(string, string, int64, string, int64) string {
	return `{"uid":12345,"package":"org.telegram.messenger"}`
}
func TestAndroidFlowOwnerMetadata(t *testing.T) {
	stopTest(t)
	SetFlowOwnerResolver(testFlowOwner{})
	defer SetFlowOwnerResolver(nil)
	m := &C.Metadata{SrcIP: netip.MustParseAddr("172.19.0.1"), DstIP: netip.MustParseAddr("192.0.2.1"), SrcPort: 4000, DstPort: 443}
	name, err := resolveFlowOwner(m)
	if err != nil || name != "org.telegram.messenger" || m.Uid != 12345 {
		t.Fatal("flow ownership not transferred")
	}
}
