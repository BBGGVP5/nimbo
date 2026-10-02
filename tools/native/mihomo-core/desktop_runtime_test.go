package mihomocore

import (
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/metacubex/mihomo/component/dialer"
	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	"net/netip"
	"strings"
	"syscall"
	"testing"
)

func TestDesktopTunCannotBeForgedByJSON(t *testing.T) {
	stopTest(t)
	r := call(t, "start", map[string]any{"yaml": mobileConfig, "options": map[string]any{"networkOwner": "desktop-tun", "dataDir": t.TempDir(), "controllerAddress": "127.0.0.1:0", "secret": strings.Repeat("s", 64)}})
	if r.Success || r.Error == nil || r.Error.Code != "PLATFORM_UNAVAILABLE" {
		t.Fatalf("untrusted owner accepted: %+v", r)
	}
}
func TestDesktopTunPreflightExactSourceAndNoSideEffects(t *testing.T) {
	stopTest(t)
	source := "# original CRLF\r\n" + strings.ReplaceAll(mobileConfig, "\n", "\r\n") + "tun: {enable: true, stack: system, auto-route: true, auto-detect-interface: true, strict-route: true}\r\n"
	r := call(t, "preflightDesktopTun", map[string]any{"yaml": source})
	requireOK(t, r)
	var data map[string]any
	if err := json.Unmarshal(r.Data, &data); err != nil {
		t.Fatal(err)
	}
	if data["sourceSHA256"] != fmt.Sprintf("%x", sha256.Sum256([]byte(source))) || data["valid"] != true {
		t.Fatalf("identity changed: %s", r.Data)
	}
	state := call(t, "status", nil)
	if !strings.Contains(string(state.Data), `"state":"stopped"`) {
		t.Fatalf("preflight mutated runtime: %s", state.Data)
	}
}
func TestDesktopTunPreflightRejectsUnownedHostControls(t *testing.T) {
	for name, extra := range map[string]string{
		"FD": "tun: {file-descriptor: 12}", "device": "tun: {device: eth0}",
		"table": "tun: {iproute2-table-index: 254}", "routes": "tun: {route-exclude-address: [0.0.0.0/0]}",
		"disable route": "tun: {auto-route: false}", "disable dns hijack": "tun: {dns-hijack: []}",
		"unknown tun": "tun: {unknown: true}", "source listener": "listeners: [{name: public, type: mixed, port: 1080}]",
		"public dns": "dns: {enable: true, listen: '0.0.0.0:53', nameserver: [1.1.1.1]}",
		"system dns": "dns: {enable: true, nameserver: [system]}",
	} {
		t.Run(name, func(t *testing.T) {
			source := mobileConfig + extra + "\n"
			if strings.HasPrefix(extra, "dns:") {
				source = "proxies: [{name: peer, type: direct}]\n" + extra + "\n"
			}
			r := call(t, "preflightDesktopTun", map[string]any{"yaml": source})
			if r.Success || r.Error == nil || r.Error.Code == "NATIVE_PANIC" {
				t.Fatalf("unsafe source admitted: %+v", r)
			}
		})
	}
}
func TestDesktopTunProjectionOwnsOnlyItsInterface(t *testing.T) {
	source := LC.Tun{Device: "unowned", FileDescriptor: 9, AutoRedirect: true, IncludeUID: []uint32{0}, IPRoute2TableIndex: 254}
	o := desktopTunProjection(source, true)
	if !o.Enable || o.Device != desktopTunName || o.FileDescriptor != 0 || !o.AutoRoute || !o.AutoDetectInterface || !o.StrictRoute || o.Stack != C.TunSystem || o.AutoRedirect || len(o.IncludeUID) != 0 || o.IPRoute2TableIndex != desktopTunTable {
		t.Fatalf("unsafe projection: %+v", o)
	}
	if len(o.Inet6Address) != 1 || len(o.DNSHijack) != 2 {
		t.Fatalf("missing IPv6/DNS: %+v", o)
	}
	if len(desktopTunProjection(source, false).Inet6Address) != 0 {
		t.Fatal("IPv6 opt-out ignored")
	}
	if source.Device != "unowned" || source.FileDescriptor != 9 {
		t.Fatal("source was mutated")
	}
}

func TestDesktopWildcardUDPEgress(t *testing.T) {
	for _, row := range []struct{ network, address, want string }{
		{"udp", ":0", "0.0.0.0"}, {"udp6", ":0", "::"}, {"udp4", "0.0.0.0:0", "0.0.0.0"},
		{"tcp4", "203.0.113.10:443", "203.0.113.10"}, {"tcp6", "[fdfe:dcba::10]:443", "fdfe:dcba::10"},
	} {
		addr, err := desktopSocketDestination(row.network, row.address)
		if err != nil || addr.String() != row.want {
			t.Fatalf("%s %s: %v %v", row.network, row.address, addr, err)
		}
	}
	if _, err := desktopSocketDestination("tcp", "host.invalid:443"); err == nil {
		t.Fatal("unresolved hostname accepted by socket hook")
	}
}

type desktopTestFinder struct {
	name  string
	calls int
}

func (f *desktopTestFinder) DefaultInterfaceName(netip.Addr) string { return f.name }
func (f *desktopTestFinder) FindInterfaceName(netip.Addr) string    { f.calls++; return f.name }

type desktopTestRaw struct{ controls int }

func (r *desktopTestRaw) Control(func(uintptr)) error {
	r.controls++
	return errors.New("fixture-control")
}
func (r *desktopTestRaw) Read(func(uintptr) bool) error  { return nil }
func (r *desktopTestRaw) Write(func(uintptr) bool) error { return nil }

var _ syscall.RawConn = (*desktopTestRaw)(nil)

func TestDesktopPhysicalEgressFailsClosedWithoutChangingHook(t *testing.T) {
	previous := dialer.DefaultInterfaceFinder.Load()
	defer dialer.DefaultInterfaceFinder.Store(previous)
	dialer.DefaultInterfaceFinder.Store(nil)
	raw := &desktopTestRaw{}
	if err := bindDesktopSocket("tcp4", "203.0.113.10:443", raw, true); err == nil {
		t.Fatal("ready runtime dialed without a physical owner")
	}
	if err := bindDesktopSocket("tcp4", "127.0.0.1:443", raw, true); err != nil {
		t.Fatal("private control loopback was bound to physical network")
	}
	if err := bindDesktopSocket("udp", ":0", raw, false); err != nil {
		t.Fatal("provider bootstrap was incorrectly treated as ready TUN")
	}
	for _, name := range []string{"", desktopTunName, "<invalid>", "<nil>"} {
		finder := &desktopTestFinder{name: name}
		dialer.DefaultInterfaceFinder.Store(finder)
		if err := bindDesktopSocket("udp", ":0", raw, true); err == nil || finder.calls != 1 {
			t.Fatal("invalid interface was permitted")
		}
	}
	if raw.controls != 0 {
		t.Fatal("socket control ran before physical admission")
	}
}
func TestDesktopProviderPathsCannotEscapeServiceHome(t *testing.T) {
	for _, resource := range []string{"/etc/shadow", "../private", "nested/../../private", `C:\private`, `..\private`} {
		for _, section := range []string{"proxy-providers", "rule-providers"} {
			definition := map[string]any{"type": "file", "path": resource}
			if section == "rule-providers" {
				definition["behavior"] = "domain"
			}
			d, err := inspect(mobileConfig)
			if err != nil {
				t.Fatal(err)
			}
			d.root[section] = map[string]any{"Fixture": definition}
			if err := desktopRuntimePolicy(d); err == nil {
				t.Fatalf("%s escaped service home: %s", section, resource)
			}
		}
	}
}
