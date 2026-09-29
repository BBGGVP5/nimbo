package mihomocore

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	D "github.com/miekg/dns"
)

func invokeStartID(t *testing.T, fields map[string]any, id string) testReply {
	t.Helper()
	fields["apiVersion"] = 1
	fields["operation"] = "start"
	fields["requestId"] = id
	b, _ := json.Marshal(fields)
	var r testReply
	if err := json.Unmarshal([]byte(Invoke(string(b))), &r); err != nil {
		t.Fatal(err)
	}
	return r
}
func TestCancelBeforeDispatchAndDuringStart(t *testing.T) {
	stopTest(t)
	defer stopTest(t)
	requireOK(t, call(t, "cancel", map[string]any{"targetRequestId": "cancel-before"}))
	r := invokeStartID(t, startFields(t, simpleConfig), "cancel-before")
	if r.Success || r.Error.Code != "START_CANCELLED" {
		t.Fatal("pre-dispatch cancellation ignored")
	}
	entered := make(chan struct{}, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { entered <- struct{}{}; <-r.Context().Done() }))
	defer server.Close()
	fields := startFields(t, fmt.Sprintf("proxy-providers: {remote: {type: http, url: %q}}\n", server.URL))
	done := make(chan testReply, 1)
	go func() { done <- invokeStartID(t, fields, "cancel-inflight") }()
	select {
	case <-entered:
	case <-time.After(5 * time.Second):
		t.Fatal("start did not reach provider")
	}
	start := time.Now()
	requireOK(t, call(t, "cancel", map[string]any{"targetRequestId": "cancel-inflight"}))
	select {
	case r := <-done:
		if r.Success {
			t.Fatal("cancelled request became ready")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("cancel didn't interrupt start")
	}
	if time.Since(start) > 2*time.Second {
		t.Fatal("cancel blocked")
	}
	requireOK(t, invokeStartID(t, startFields(t, simpleConfig), "committed-unique"))
	late := call(t, "cancel", map[string]any{"targetRequestId": "committed-unique"})
	if late.Success || late.Error.Code != "ALREADY_COMMITTED" {
		t.Fatal("late cancel not distinguished")
	}
	requireOK(t, call(t, "status", nil))
}
func TestConcurrentStatusStopIdempotent(t *testing.T) {
	startTest(t, simpleConfig)
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 5; j++ {
				r := call(t, "status", nil)
				requireOK(t, r)
			}
			requireOK(t, call(t, "stop", nil))
		}()
	}
	wg.Wait()
	r := call(t, "status", nil)
	if !strings.Contains(string(r.Data), `"state":"stopped"`) {
		t.Fatal(string(r.Data))
	}
}
func TestStrictJSONDuplicateFields(t *testing.T) {
	for _, input := range []string{`{"apiVersion":1,"apiVersion":1,"operation":"status"}`, `{"apiVersion":1,"operation":"start","options":{"secret":"a","secret":"b"}}`} {
		var r testReply
		_ = json.Unmarshal([]byte(Invoke(input)), &r)
		if r.Success || r.Error.Code != "INVALID_REQUEST" {
			t.Fatal("duplicate request field accepted")
		}
	}
}

type panicProtector struct{}

func TestProviderFilterAlternativeOrdering(t *testing.T) {
	parser, err := strictProxyParser("ordered", map[string]any{"filter": "^a`^b"})
	if err != nil {
		t.Fatal(err)
	}
	proxies, err := parser([]byte("proxies: [{name: b1, type: direct}, {name: a1, type: direct}, {name: a2, type: direct}]"))
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		for _, p := range proxies {
			_ = p.Close()
		}
	}()
	for i, name := range []string{"a1", "a2", "b1"} {
		if proxies[i].Name() != name {
			t.Fatal("native filter ordering changed")
		}
	}
}

func (panicProtector) Protect(int64) bool { panic("synthetic callback failure") }
func TestProtectionCallbackPanicFailsClosed(t *testing.T) {
	stopTest(t)
	SetSocketProtector(panicProtector{})
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { t.Error("unprotected request reached destination") }))
	defer server.Close()
	startTest(t, simpleConfig)
	if call(t, "delay", map[string]any{"name": "DIRECT", "url": server.URL, "timeoutMs": 500}).Success {
		t.Fatal("panic in protection callback bypassed protection")
	}
}
func TestInternalDNSAndHostUnchanged(t *testing.T) {
	packet, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	var queries atomic.Int32
	server := &D.Server{PacketConn: packet, Handler: D.HandlerFunc(func(w D.ResponseWriter, r *D.Msg) {
		queries.Add(1)
		reply := new(D.Msg)
		reply.SetReply(r)
		for _, q := range r.Question {
			if q.Qtype == D.TypeA {
				reply.Answer = append(reply.Answer, &D.A{Hdr: D.RR_Header{Name: q.Name, Rrtype: D.TypeA, Class: D.ClassINET, Ttl: 1}, A: net.ParseIP("127.0.0.1")})
			}
		}
		_ = w.WriteMsg(reply)
	})}
	go func() { _ = server.ActivateAndServe() }()
	defer server.Shutdown()
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, "internal-dns") }))
	defer target.Close()
	_, port, _ := net.SplitHostPort(strings.TrimPrefix(target.URL, "http://"))
	source := simpleConfig + fmt.Sprintf("dns:\n  enable: true\n  use-system-hosts: false\n  nameserver: ['udp://%s']\n  default-nameserver: ['%s']\n  fallback-filter: {geoip: false}\n", packet.LocalAddr(), packet.LocalAddr())
	_, s := startTest(t, source)
	code, body, err := proxyGet(s.MixedAddress, "http://nimbo-synthetic.invalid:"+port+"/")
	if err != nil || code != 200 || body != "internal-dns" || queries.Load() == 0 {
		t.Fatalf("internal DNS resolver not exercised: %d %s %v queries=%d", code, body, err, queries.Load())
	}
}
func TestRuleProviderRefreshAtomic(t *testing.T) {
	var payload atomic.Value
	payload.Store("payload: [127.0.0.0/8]\n")
	remote := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, payload.Load().(string)) }))
	defer remote.Close()
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, "rules-live") }))
	defer target.Close()
	source := fmt.Sprintf("rule-providers:\n  Rules: {type: http, behavior: ipcidr, url: %q}\nrules: [\"RULE-SET,Rules,DIRECT,no-resolve\",\"MATCH,REJECT\"]\n", remote.URL)
	_, s := startTest(t, source)
	if code, _, err := proxyGet(s.MixedAddress, target.URL); err != nil || code != 200 {
		t.Fatal("initial actual rule-set not routing")
	}
	payload.Store("payload: [invalid-cidr]\n")
	if call(t, "refreshRuleProvider", map[string]any{"name": "Rules"}).Success {
		t.Fatal("invalid rule silently dropped")
	}
	if code, _, err := proxyGet(s.MixedAddress, target.URL); err != nil || code != 200 {
		t.Fatal("invalid refresh lost last valid rules")
	}
	payload.Store("payload: [192.0.2.0/24]\n")
	requireOK(t, call(t, "refreshRuleProvider", map[string]any{"name": "Rules"}))
	if code, _, err := proxyGet(s.MixedAddress, target.URL); err == nil && code == 200 {
		t.Fatal("new rule provider did not change actual routing")
	}
}
func TestNativeDynamicGroupTypes(t *testing.T) {
	health := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer health.Close()
	source := fmt.Sprintf(`proxies: [{name: z, type: direct}, {name: a, type: direct}]
proxy-groups:
 - {name: Auto, type: url-test, include-all-proxies: true, url: %q, interval: 3600}
 - {name: Failover, type: fallback, proxies: [a, z], url: %q, interval: 3600}
 - {name: Balanced, type: load-balance, proxies: [a, z], strategy: round-robin, url: %q, interval: 3600}
 - {name: Choice, type: select, proxies: [Auto, Failover, Balanced]}
rules: ["MATCH,Choice"]
`, health.URL, health.URL, health.URL)
	startTest(t, source)
	r := call(t, "snapshot", nil)
	requireOK(t, r)
	var data struct {
		Groups map[string]struct {
			All  []string `json:"all"`
			Type string   `json:"type"`
		} `json:"groups"`
	}
	_ = json.Unmarshal(r.Data, &data)
	if got := data.Groups["Auto"].All; len(got) != 2 || got[0] != "a" || got[1] != "z" {
		t.Fatalf("native lexical inclusion semantics changed: %v", got)
	}
	for _, g := range []string{"Auto", "Failover"} {
		requireOK(t, call(t, "select", map[string]any{"group": g, "name": "z"}))
	}
	if call(t, "select", map[string]any{"group": "Balanced", "name": "a"}).Success {
		t.Fatal("load-balance advertised as selector")
	}
}
