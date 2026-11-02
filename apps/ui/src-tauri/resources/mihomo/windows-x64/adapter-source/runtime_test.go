package mihomocore

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

const simpleConfig = `mode: rule
proxies:
  - {name: local, type: direct}
proxy-groups:
  - {name: Choice, type: select, proxies: [local, DIRECT, REJECT]}
rules: ["MATCH,Choice"]
`

type testReply struct {
	APIVersion int             `json:"apiVersion"`
	RequestID  string          `json:"requestId"`
	Success    bool            `json:"success"`
	Generation uint64          `json:"generation"`
	Data       json.RawMessage `json:"data"`
	Error      *issue          `json:"error"`
}

func call(t *testing.T, operation string, fields map[string]any) testReply {
	t.Helper()
	if fields == nil {
		fields = map[string]any{}
	}
	fields["apiVersion"] = 1
	fields["requestId"] = "test-" + operation
	fields["operation"] = operation
	b, _ := json.Marshal(fields)
	var r testReply
	s := Invoke(string(b))
	if err := json.Unmarshal([]byte(s), &r); err != nil {
		t.Fatalf("invalid response %q: %v", s, err)
	}
	if r.RequestID != "test-"+operation || r.APIVersion != 1 {
		t.Fatalf("correlation: %s", s)
	}
	return r
}
func TestCapabilitiesExposeOwnerMatrix(t *testing.T) {
	r := call(t, "capabilities", nil)
	requireOK(t, r)
	var caps capabilitySet
	if err := json.Unmarshal(r.Data, &caps); err != nil {
		t.Fatal(err)
	}
	if !caps.DesktopProxy || caps.IOSVPN || caps.IPv6 != androidTunCompiled || caps.RuleRouting != androidTunCompiled ||
		!caps.GroupAuto || !caps.HealthChecks || !caps.ProviderAuto {
		t.Fatalf("unexpected capability matrix: %+v", caps)
	}
}

func requireOK(t *testing.T, r testReply) {
	t.Helper()
	if !r.Success {
		t.Fatalf("operation failed: %+v", r.Error)
	}
}
func stopTest(t *testing.T) { t.Helper(); requireOK(t, call(t, "stop", nil)); SetSocketProtector(nil) }
func startFields(t *testing.T, source string) map[string]any {
	return map[string]any{"yaml": source, "options": map[string]any{"dataDir": t.TempDir(), "networkOwner": "desktop-proxy", "mixedAddress": "127.0.0.1:0", "controllerAddress": "127.0.0.1:0", "secret": strings.Repeat("test-secret-", 4)}}
}
func startTest(t *testing.T, source string) (testReply, runtimeStatus) {
	t.Helper()
	fields := startFields(t, source) // TempDir cleanup must run AFTER runtime cleanup.
	t.Cleanup(func() { stopTest(t) })
	r := call(t, "start", fields)
	requireOK(t, r)
	var s runtimeStatus
	if err := json.Unmarshal(r.Data, &s); err != nil {
		t.Fatal(err)
	}
	return r, s
}
func proxyGet(address, target string) (int, string, error) {
	u, _ := url.Parse("http://" + address)
	tr := &http.Transport{Proxy: http.ProxyURL(u)}
	defer tr.CloseIdleConnections()
	c := &http.Client{Transport: tr, Timeout: 2 * time.Second}
	r, err := c.Get(target)
	if err != nil {
		return 0, "", err
	}
	defer r.Body.Close()
	b, err := io.ReadAll(r.Body)
	return r.StatusCode, string(b), err
}

func TestInspectExactSourceAndNonNullCollections(t *testing.T) {
	source := "# public source\r\nmode: rule\r\nunknown-new-feature: {x: [1, 2]}\r\n"
	r := call(t, "inspect", map[string]any{"yaml": source})
	requireOK(t, r)
	var d inspection
	if err := json.Unmarshal(r.Data, &d); err != nil {
		t.Fatal(err)
	}
	if d.OriginalYAML != source || d.SourceSHA256 != fmt.Sprintf("%x", sha256.Sum256([]byte(source))) {
		t.Fatal("source/hash changed")
	}
	if d.DeclaredGraph.Proxies == nil || d.DeclaredGraph.Groups == nil || d.DeclaredGraph.Providers == nil || len(d.StrictIssues) != 1 {
		t.Fatalf("collections/issues: %s", r.Data)
	}
	if call(t, "validate", map[string]any{"yaml": source}).Success {
		t.Fatal("unknown feature dropped")
	}
	empty := call(t, "inspect", map[string]any{"yaml": "mode: rule\n"})
	requireOK(t, empty)
	if !strings.Contains(string(empty.Data), `"strictIssues":[]`) {
		t.Fatal(string(empty.Data))
	}
}
func TestInspectAliasesAndDynamicGraph(t *testing.T) {
	source := `base: &b {type: select, use: [remote], include-all-providers: true}
proxy-groups: [{name: Choice, <<: *b, filter: "^EU", hidden: true}]
proxy-providers: {remote: {type: http, url: "https://example.invalid/public.yaml", interval: 3600}}
`
	r := call(t, "inspect", map[string]any{"yaml": source})
	requireOK(t, r)
	var d inspection
	_ = json.Unmarshal(r.Data, &d)
	g := d.DeclaredGraph.Groups[0]
	if g["include-all-providers"] != true || g["filter"] != "^EU" || g["use"] == nil || d.OriginalYAML != source {
		t.Fatalf("dynamic graph flattened: %s", r.Data)
	}
}
func TestInvalidYAMLShapes(t *testing.T) {
	for _, s := range []string{"x: 1\nx: 2", "mode: rule\n---\nmode: direct", "x: &a [*a]", "[1,2]", "proxies: {}", "proxy-providers: []", "x: .nan", string([]byte{0xff})} {
		t.Run(fmt.Sprintf("%x", sha256.Sum256([]byte(s)))[:8], func(t *testing.T) {
			if _, err := inspect(s); err == nil {
				t.Fatal("invalid YAML accepted")
			}
		})
	}
}
func TestActualNativeValidation(t *testing.T) {
	stopTest(t)
	for name, source := range map[string]string{
		"valid":               simpleConfig,
		"cycle":               "proxy-groups: [{name: A, type: select, proxies: [B]}, {name: B, type: select, proxies: [A]}]",
		"missing":             "proxy-groups: [{name: A, type: select, proxies: [missing]}]",
		"reserved":            "proxies: [{name: DIRECT, type: direct}]",
		"bad-rule":            "rules: [DOMAIN,not-a-rule]",
		"unknown-proxy-field": "proxies: [{name: test, type: direct, made-up: true}]",
		"unsupported-relay":   "proxy-groups: [{name: X, type: relay, proxies: [DIRECT]}]",
		"missing-provider":    "proxy-groups: [{name: X, type: select, use: [absent]}]",
	} {
		t.Run(name, func(t *testing.T) {
			r := call(t, "validate", map[string]any{"yaml": source})
			if r.Success != (name == "valid") {
				t.Fatalf("unexpected %s result %+v", name, r)
			}
		})
	}
}

func TestUnsafeOwnershipRejectedBeforeBind(t *testing.T) {
	for _, extra := range []string{"tun: {enable: true}", "mixed-port: 12345", "external-controller: 0.0.0.0:9090", "allow-lan: true", "iptables: {enable: true}", "ntp: {enable: true}", "dns: {enable: true, listen: 0.0.0.0:53}", "rules: [\"GEOIP,CN,DIRECT\"]"} {
		r := call(t, "start", startFields(t, "mode: rule\n"+extra))
		if r.Success {
			stopTest(t)
			t.Fatalf("unsafe config accepted: %s", extra)
		}
	}
	f := startFields(t, simpleConfig)
	f["options"].(map[string]any)["mixedAddress"] = "0.0.0.0:0"
	if call(t, "start", f).Success {
		t.Fatal("wildcard bind accepted")
	}
}

func TestLoopbackProxySelectionControllerAndStop(t *testing.T) {
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, "actual-mihomo") }))
	defer target.Close()
	first, s := startTest(t, simpleConfig)
	if s.State != "running" || s.CoreCommit != coreCommit {
		t.Fatalf("bad readiness %+v", s)
	}
	if code, body, err := proxyGet(s.MixedAddress, target.URL); err != nil || code != 200 || body != "actual-mihomo" {
		t.Fatalf("real proxy: %d %q %v", code, body, err)
	}
	selectReply := call(t, "select", map[string]any{"group": "Choice", "name": "REJECT", "generation": first.Generation})
	requireOK(t, selectReply)
	if !strings.Contains(string(selectReply.Data), `"now":"REJECT"`) {
		t.Fatalf("no readback %s", selectReply.Data)
	}
	if code, _, err := proxyGet(s.MixedAddress, target.URL); err == nil && code == 200 {
		t.Fatal("REJECT selection ignored")
	}
	requireOK(t, call(t, "select", map[string]any{"group": "Choice", "name": "DIRECT"}))
	if call(t, "select", map[string]any{"group": "Choice", "name": "absent"}).Success {
		t.Fatal("invalid selection accepted")
	}
	requestBody := `{"apiVersion":1,"requestId":"control","operation":"status"}`
	req, _ := http.NewRequest(http.MethodPost, "http://"+s.ControllerAddress+"/v1/invoke", strings.NewReader(requestBody))
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != 401 {
		t.Fatal("controller unauthenticated")
	}
	req, _ = http.NewRequest(http.MethodPost, "http://"+s.ControllerAddress+"/v1/invoke", strings.NewReader(requestBody))
	req.Header.Set("Authorization", "Bearer "+strings.Repeat("test-secret-", 4))
	resp, err = http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	b, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if !strings.Contains(string(b), `"success":true`) {
		t.Fatalf("control: %s", b)
	}
	requireOK(t, call(t, "delay", map[string]any{"name": "DIRECT", "url": target.URL, "timeoutMs": 1000, "expectedStatus": "200"}))
	requireOK(t, call(t, "nimboDelay", map[string]any{"name": "local", "url": target.URL, "timeoutMs": 1000, "expectedStatus": "200"}))
	if call(t, "nimboDelay", map[string]any{"name": "Choice", "url": target.URL, "timeoutMs": 1000, "expectedStatus": "200"}).Success {
		t.Fatal("Nimbo Ping must not measure a group instead of a concrete node")
	}
	if call(t, "start", startFields(t, simpleConfig)).Success {
		t.Fatal("second instance allowed")
	}
	pending, err := net.Dial("tcp", s.MixedAddress)
	if err != nil {
		t.Fatal(err)
	}
	defer pending.Close()
	time.Sleep(20 * time.Millisecond)
	requireOK(t, call(t, "stop", map[string]any{"generation": first.Generation}))
	_ = pending.SetReadDeadline(time.Now().Add(time.Second))
	one := make([]byte, 1)
	if _, err = pending.Read(one); err == nil {
		t.Fatal("accepted handshake socket leaked")
	}
	for _, address := range []string{s.MixedAddress, s.ControllerAddress} {
		if c, err := net.DialTimeout("tcp", address, 100*time.Millisecond); err == nil {
			c.Close()
			t.Fatalf("listener survived stop %s", address)
		}
	}
	second, _ := startTest(t, simpleConfig)
	if second.Generation != first.Generation+1 {
		t.Fatal("generation did not advance")
	}
	stale := call(t, "stop", map[string]any{"generation": first.Generation})
	if stale.Success {
		t.Fatal("stale stop killed current runtime")
	}
	status := call(t, "status", nil)
	if !strings.Contains(string(status.Data), `"state":"running"`) {
		t.Fatal("stale stop changed state")
	}
}

func TestProviderRefreshAtomicAndDynamicMembership(t *testing.T) {
	var document atomic.Value
	document.Store("proxies: [{name: first, type: direct}]\n")
	remote := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, document.Load().(string)) }))
	defer remote.Close()
	source := fmt.Sprintf("proxy-providers: {Remote: {type: http, url: %q}}\nproxy-groups: [{name: Choice, type: select, include-all-providers: true}]\nrules: [MATCH,Choice]\n", remote.URL)
	// Quote MATCH rule correctly: YAML flow plain strings split on commas.
	source = strings.ReplaceAll(source, "[MATCH,Choice]", "[\"MATCH,Choice\"]")
	_, s := startTest(t, source)
	_ = s
	before := call(t, "snapshot", nil)
	requireOK(t, before)
	if !strings.Contains(string(before.Data), `"first"`) {
		t.Fatal(string(before.Data))
	}
	document.Store("proxies: [{name: second, type: direct}]\n")
	requireOK(t, call(t, "refreshProvider", map[string]any{"name": "Remote"}))
	requireOK(t, call(t, "select", map[string]any{"group": "Choice", "name": "second"}))
	for _, bad := range []string{"proxies: [{name: third, type: direct, typo: true}]", "proxies: [{type: direct}]", "proxies: [{name: dupe, type: direct}, {name: dupe, type: direct}]"} {
		document.Store(bad)
		if call(t, "refreshProvider", map[string]any{"name": "Remote"}).Success {
			t.Fatal("bad provider replaced membership")
		}
		after := call(t, "snapshot", nil)
		if !strings.Contains(string(after.Data), `"now":"second"`) {
			t.Fatalf("previous membership lost: %s", after.Data)
		}
	}
}

func TestStartRollbackAndCancellation(t *testing.T) {
	stopTest(t)
	occupied, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer occupied.Close()
	f := startFields(t, simpleConfig)
	f["options"].(map[string]any)["mixedAddress"] = occupied.Addr().String()
	before := call(t, "status", nil)
	if call(t, "start", f).Success {
		t.Fatal("bound occupied port")
	}
	after := call(t, "status", nil)
	if after.Generation != before.Generation || !strings.Contains(string(after.Data), `"state":"stopped"`) {
		t.Fatal("rollback state")
	}
	started := make(chan struct{}, 1)
	remote := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { started <- struct{}{}; <-r.Context().Done() }))
	defer remote.Close()
	source := fmt.Sprintf("proxy-providers: {Remote: {type: http, url: %q}}\nproxy-groups: [{name: Choice, type: select, use: [Remote]}]", remote.URL)
	fields := startFields(t, source)
	done := make(chan testReply, 1)
	go func() { done <- call(t, "start", fields) }()
	select {
	case <-started:
	case <-time.After(5 * time.Second):
		t.Fatal("provider fetch did not start")
	}
	start := time.Now()
	requireOK(t, call(t, "stop", nil))
	if time.Since(start) > 2*time.Second {
		t.Fatal("stop queued behind start")
	}
	if r := <-done; r.Success {
		t.Fatal("cancelled start published readiness")
	}
	startTest(t, simpleConfig)
}

func TestStopInterruptsDelayAndRefresh(t *testing.T) {
	for _, operation := range []string{"delay", "refreshProvider"} {
		t.Run(operation, func(t *testing.T) {
			stopTest(t)
			var block atomic.Bool
			entered := make(chan struct{}, 1)
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if block.Load() {
					select {
					case entered <- struct{}{}:
					default:
					}
					<-r.Context().Done()
					return
				}
				fmt.Fprint(w, "proxies: [{name: node, type: direct}]")
			}))
			defer server.Close()
			source := simpleConfig
			if operation == "refreshProvider" {
				source += fmt.Sprintf("proxy-providers: {Remote: {type: http, url: %q}}\n", server.URL)
			}
			startTest(t, source)
			block.Store(true)
			done := make(chan testReply, 1)
			go func() {
				if operation == "delay" {
					done <- call(t, operation, map[string]any{"name": "DIRECT", "url": server.URL, "timeoutMs": 30000})
				} else {
					done <- call(t, operation, map[string]any{"name": "Remote"})
				}
			}()
			select {
			case <-entered:
			case <-time.After(5 * time.Second):
				t.Fatal("operation not running")
			}
			start := time.Now()
			requireOK(t, call(t, "stop", nil))
			if time.Since(start) > 2*time.Second {
				t.Fatal("stop failed to cancel running operation")
			}
			if r := <-done; r.Success {
				t.Fatal("cancelled operation succeeded")
			}
		})
	}
}

func TestApplicationRuleProviderRouting(t *testing.T) {
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, "rule-routed") }))
	defer target.Close()
	source := `rule-providers:
  localnet: {type: inline, behavior: ipcidr, payload: [127.0.0.0/8]}
rules: ["RULE-SET,localnet,DIRECT,no-resolve", "MATCH,REJECT"]
`
	_, s := startTest(t, source)
	if code, body, err := proxyGet(s.MixedAddress, target.URL); err != nil || code != 200 || body != "rule-routed" {
		t.Fatalf("actual rule provider was not applied: %d %q %v", code, body, err)
	}
}

type denyProtector struct{ calls atomic.Int32 }

func (p *denyProtector) Protect(int64) bool { p.calls.Add(1); return false }
func TestSocketProtectionFailsClosed(t *testing.T) {
	stopTest(t)
	p := &denyProtector{}
	SetSocketProtector(p)
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { t.Error("unprotected connection reached server") }))
	defer target.Close()
	startTest(t, simpleConfig)
	if call(t, "delay", map[string]any{"name": "DIRECT", "url": target.URL, "timeoutMs": 500}).Success {
		t.Fatal("protection false ignored")
	}
	if p.calls.Load() == 0 {
		t.Fatal("socket hook not invoked")
	}
}
func TestMobileGateAndInvalidFD(t *testing.T) {
	for _, fd := range []int64{-1, 0, 42} {
		var r testReply
		_ = json.Unmarshal([]byte(StartIOS(`{"requestId":"ios"}`, fd)), &r)
		if r.Success || r.Error == nil {
			t.Fatal("fake mobile success")
		}
		if fd <= 0 && r.Error.Code != "INVALID_FD" {
			t.Fatal(r.Error)
		}
	}
}
func TestWireGolden(t *testing.T) {
	b, err := os.ReadFile("testdata/inspect-wire-v1.json")
	if os.IsNotExist(err) {
		t.Skip("generated from source-built CLI after first build")
	}
	if err != nil {
		t.Fatal(err)
	}
	var golden testReply
	if err = json.Unmarshal(b, &golden); err != nil {
		t.Fatal(err)
	}
	var d inspection
	if err = json.Unmarshal(golden.Data, &d); err != nil {
		t.Fatal(err)
	}
	req, _ := json.Marshal(map[string]any{"apiVersion": 1, "requestId": golden.RequestID, "operation": "inspect", "yaml": d.OriginalYAML})
	var actual testReply
	_ = json.Unmarshal([]byte(Invoke(string(req))), &actual)
	if !actual.Success || string(actual.Data) != string(golden.Data) {
		t.Fatalf("golden differs: %s", actual.Data)
	}
}
