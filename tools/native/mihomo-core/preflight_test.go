package mihomocore

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/component/resolver"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

func TestPreflightAndroidPolicyOnly(t *testing.T) {
	stopTest(t)
	for name, source := range map[string]string{
		"direct":     mobileConfig,
		"exact CRLF": "# preserve original bytes\r\n" + strings.ReplaceAll(mobileConfig, "\n", "\r\n"),
		// These resources do not need to exist for source-only admission.
		"file provider":   mobileConfig + "proxy-providers: {remote: {type: file, path: absent/preflight.yaml}}\n",
		"HTTP provider":   mobileConfig + "proxy-providers: {remote: {type: http, url: 'https://preflight.invalid/proxies.yaml', interval: 0}}\n",
		"inline provider": mobileConfig + "proxy-providers: {remote: {type: inline, payload: [{name: peer, type: socks5, server: 192.0.2.10, port: 1080, udp: true}]}}\n",
	} {
		t.Run(name, func(t *testing.T) {
			r := call(t, "preflightAndroid", map[string]any{"yaml": source})
			requireOK(t, r)
			var data map[string]any
			if err := json.Unmarshal(r.Data, &data); err != nil {
				t.Fatal(err)
			}
			want := map[string]any{"valid": true, "sourceSHA256": fmt.Sprintf("%x", sha256.Sum256([]byte(source))), "scope": "upstream-mihomo-android-vpn"}
			if !reflect.DeepEqual(data, want) {
				t.Fatalf("policy result = %s; want %v", r.Data, want)
			}
		})
	}
}

func TestInspectClassifiesFromParsedRootKeys(t *testing.T) {
	d, err := inspect("# proxy-groups: text only\nmode: rule\n")
	if err != nil {
		t.Fatal(err)
	}
	if d.DocumentKind != "mihomo" || !reflect.DeepEqual(d.RootKeys, []string{"mode"}) {
		t.Fatalf("classification must use parsed keys, got kind=%q keys=%v", d.DocumentKind, d.RootKeys)
	}
	plain, err := inspect("name: ordinary yaml\nvalue: 1\n")
	if err != nil {
		t.Fatal(err)
	}
	if plain.DocumentKind != "yaml" {
		t.Fatalf("unrelated YAML classified as %q", plain.DocumentKind)
	}
}

func TestPreflightAndroidRejectsPlatformOwnedAndUnsafeConfigs(t *testing.T) {
	for name, source := range map[string]string{
		"empty":                       "",
		"duplicate YAML":              mobileConfig + "mode: global\n",
		"multiple documents":          mobileConfig + "---\nmode: global\n",
		"unknown field":               mobileConfig + "unknown-feature: true\n",
		"physical interface":          strings.Replace(mobileConfig, "type: direct", "type: socks5, interface-name: wlan0", 1),
		"routing mark":                mobileConfig + "routing-mark: 12\n",
		"DHCP DNS":                    strings.Replace(mobileConfig, "127.0.0.1:5353", "dhcp://system", 1),
		"disabled DNS":                strings.Replace(mobileConfig, "enable: true", "enable: false", 1),
		"DNS listener":                strings.Replace(mobileConfig, "  enable: true", "  enable: true\n  listen: 127.0.0.1:53", 1),
		"system DNS appended":         mobileConfig + "  append-system-dns: true\n",
		"unknown nested DNS key":      strings.Replace(mobileConfig, "  nameserver: [127.0.0.1:5353]", "  nameserver: [127.0.0.1:5353]\n  made-up-option: true", 1),
		"DNS host route mark":         strings.Replace(mobileConfig, "  enable: true", "  enable: true\n  listen-routing-mark: 10", 1),
		"explicit MMDB mode":          mobileConfig + "geodata-mode: false\n",
		"unknown rule provider field": mobileConfig + "rule-providers: {feed: {type: inline, behavior: domain, payload: ['example.com'], future-option: true}}\n",
	} {
		t.Run(name, func(t *testing.T) {
			r := call(t, "preflightAndroid", map[string]any{"yaml": source})
			if r.Success || r.Error == nil || r.Error.Code == "NATIVE_PANIC" || r.Error.Code == "NOT_RUNNING" || len(r.Data) != 0 {
				t.Fatalf("expected policy rejection: %+v", r)
			}
		})
	}
	for _, proxy := range []string{
		"type: vless, network: grpc", "type: vless, network: xhttp", "type: trojan, network: ws",
		"type: hysteria2", "type: tuic", "type: vmess", "type: anytls",
		"type: ss, cipher: 2022-blake3-aes-128-gcm",
	} {
		t.Run(proxy, func(t *testing.T) {
			r := call(t, "preflightAndroid", map[string]any{"yaml": strings.Replace(mobileConfig, "type: direct", proxy, 1)})
			if !r.Success {
				t.Fatalf("pinned upstream protocol/transport was incorrectly excluded: %+v", r.Error)
			}
		})
	}
}

func TestPreflightAndroidAdmitsNativeMihomoFeatures(t *testing.T) {
	for name, source := range map[string]string{
		"physical system DNS":          strings.Replace(mobileConfig, "127.0.0.1:5353", "system", 1),
		"dual stack opt-in":            strings.Replace(mobileConfig, "mode: global", "mode: global\nipv6: true", 1),
		"DoH":                          strings.Replace(mobileConfig, "127.0.0.1:5353", "https://1.1.1.1/dns-query", 1),
		"automatic group":              strings.Replace(mobileConfig, "type: select", "type: url-test", 1),
		"proxy provider":               mobileConfig + "proxy-providers: {remote: {type: http, url: 'https://preflight.invalid/p', interval: 60}}\n",
		"provider health":              mobileConfig + "proxy-providers: {remote: {type: inline, health-check: {enable: true, url: 'https://1.1.1.1/', interval: 60}, payload: [{name: peer, type: direct}]}}\n",
		"full native provider options": mobileConfig + "proxy-providers: {remote: {type: http, url: 'https://preflight.invalid/p', interval: 60, proxy: DIRECT, size-limit: 1048576, age-secret-key: secret, header: {Authorization: [token]}, override: {additional-prefix: 'feed:', udp: true}}}\n",
		"MRS rule provider":            strings.Replace(mobileConfig, "mode: global", "mode: rule", 1) + "rule-providers: {rules: {type: http, behavior: domain, format: mrs, url: 'https://preflight.invalid/rules.mrs', interval: 86400, size-limit: 10485760, proxy: DIRECT, header: {User-Agent: [Nimbo]}}}\nrules: ['RULE-SET,rules,GLOBAL']\n",
		"rule mode":                    strings.Replace(mobileConfig, "mode: global", "mode: rule", 1) + "rules: ['MATCH,GLOBAL']\n",
	} {
		t.Run(name, func(t *testing.T) {
			r := call(t, "preflightAndroid", map[string]any{"yaml": source})
			if !r.Success {
				t.Fatalf("native Mihomo feature was rejected: %+v", r.Error)
			}
		})
	}
}

func TestPreflightAndroidAdmitsProtectedVlessRealityTCP(t *testing.T) {
	key := base64.RawURLEncoding.EncodeToString(append([]byte{9}, make([]byte, 31)...))
	proxy := fmt.Sprintf("type: vless, server: 192.0.2.10, port: 443, uuid: 11111111-1111-4111-8111-111111111111, tls: true, servername: example.com, network: tcp, flow: xtls-rprx-vision, client-fingerprint: chrome, reality-opts: {public-key: %s, short-id: ab12}", key)
	source := strings.Replace(mobileConfig, "type: direct", proxy, 1)
	for _, operation := range []string{"preflightAndroid", "validate"} {
		r := call(t, operation, map[string]any{"yaml": source})
		requireOK(t, r)
	}
	// Preflight is intentionally side-effect-free source/platform admission.
	// Full protocol parsing occurs through the pinned upstream parser on start.
	for _, invalid := range []string{
		strings.Replace(source, "network: tcp", "interface-name: wlan0, network: tcp", 1),
		strings.Replace(source, "network: tcp", "routing-mark: 1, network: tcp", 1),
	} {
		r := call(t, "preflightAndroid", map[string]any{"yaml": invalid})
		if r.Success {
			t.Fatal("Android-owned route boundary was not enforced")
		}
	}
}

func TestPreflightAndroidLeavesActiveRuntimeUnaffected(t *testing.T) {
	m, protector := mobileFixture(t)
	before := call(t, "status", nil)
	requireOK(t, before)
	sessionBefore := singleton.session
	dnsBefore, homeBefore, ipv6Before := saveDNS(), C.Path.HomeDir(), resolver.DisableIPv6
	modeBefore, processBefore, logBefore := tunnel.Mode(), tunnel.FindProcessMode(), log.Level()
	selector := m.cfg.Proxies["GLOBAL"].Adapter().(*outboundgroup.Selector)
	selectionBefore := selector.Now()
	protectBefore := protector.calls.Load()

	// Hold the native-operation lock to model an in-flight delay/refresh. Pure
	// admission must complete without waiting for or touching that operation.
	singleton.op.Lock()
	locked := true
	defer func() {
		if locked {
			singleton.op.Unlock()
		}
	}()
	for _, source := range []string{mobileConfig, mobileConfig + "unknown-feature: true\n", mobileConfig + "mixed-port: 7890\n"} {
		input, err := json.Marshal(map[string]any{"apiVersion": 1, "requestId": "active-preflight", "operation": "preflightAndroid", "yaml": source, "generation": before.Generation})
		if err != nil {
			t.Fatal(err)
		}
		done := make(chan string, 1)
		go func() { done <- Invoke(string(input)) }()
		var raw string
		select {
		case raw = <-done:
		case <-time.After(2 * time.Second):
			singleton.op.Unlock()
			locked = false
			<-done
			t.Fatal("preflight waited for native operation lock")
		}
		var reply testReply
		if err := json.Unmarshal([]byte(raw), &reply); err != nil {
			t.Fatal(err)
		}
		if reply.Success != !strings.Contains(source, "unknown-feature") || reply.Generation != before.Generation || reply.RequestID != "active-preflight" {
			t.Fatalf("unexpected active preflight: %s", raw)
		}
	}
	singleton.op.Unlock()
	locked = false
	after := call(t, "status", nil)
	if !reflect.DeepEqual(before, after) || singleton.session != sessionBefore || m.ctx.Err() != nil {
		t.Fatal("preflight altered the active runtime")
	}
	if !reflect.DeepEqual(dnsBefore, saveDNS()) || homeBefore != C.Path.HomeDir() || ipv6Before != resolver.DisableIPv6 || modeBefore != tunnel.Mode() || processBefore != tunnel.FindProcessMode() || logBefore != log.Level() {
		t.Fatal("preflight altered native globals")
	}
	if protector.calls.Load() != protectBefore || selector.Now() != selectionBefore {
		t.Fatal("preflight dialed or changed the active selector")
	}
	stale := call(t, "preflightAndroid", map[string]any{"yaml": mobileConfig, "generation": before.Generation + 1})
	if stale.Success || stale.Error == nil || stale.Error.Code != "STALE_GENERATION" {
		t.Fatal("preflight bypassed envelope generation guard")
	}
	busy := call(t, "validate", map[string]any{"yaml": mobileConfig})
	if busy.Success || busy.Error == nil || busy.Error.Code != "BUSY" {
		t.Fatal("native validation must remain stopped-only")
	}
}
