package mihomocore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/metacubex/mihomo/component/resolver"
	mihomoDNS "github.com/metacubex/mihomo/dns"
	D "github.com/miekg/dns"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestProbeFailureReportsOnlySafeFixedCodes(t *testing.T) {
	cases := []struct {
		err  error
		dial bool
		code string
	}{
		{errors.New("private.example connect error: REALITY authentication failed"), true, "PROBE_REALITY_AUTH_FAILED"},
		{errors.New("private.example connect error: dns resolve failed"), true, "PROBE_DNS_FAILED"},
		{context.DeadlineExceeded, true, "PROBE_TIMEOUT"},
		{errors.New("platform socket protection failed"), true, "PROBE_PROTECTION_FAILED"},
		{errors.New("private.example: unknown dial error"), true, "PROBE_DIAL_FAILED"},
		{errors.New("private.example: GET failed"), false, "PROBE_GET_FAILED"},
	}
	for _, tc := range cases {
		failure := probeFailure(tc.err, tc.dial).(*issue)
		if failure.Code != tc.code || strings.Contains(failure.Message, "private.example") {
			t.Fatalf("unsafe or incorrect probe failure: %+v", failure)
		}
	}
}

func TestStandaloneProbeUsesSubscriptionProxyDNS(t *testing.T) {
	stopTest(t)
	udp, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	server := &D.Server{PacketConn: udp, Handler: D.HandlerFunc(func(w D.ResponseWriter, r *D.Msg) {
		response := new(D.Msg)
		response.SetReply(r)
		for _, question := range r.Question {
			if question.Qtype == D.TypeA {
				response.Answer = append(response.Answer, &D.A{Hdr: D.RR_Header{Name: question.Name, Rrtype: D.TypeA, Class: D.ClassINET, Ttl: 60}, A: net.IPv4(127, 0, 0, 9)})
			}
		}
		_ = w.WriteMsg(response)
	})}
	go func() { _ = server.ActivateAndServe() }()
	defer server.Shutdown()
	address := udp.LocalAddr().String()
	source := fmt.Sprintf("dns:\n  enable: true\n  nameserver: [192.0.2.1]\n  proxy-server-nameserver: [%q]\n  nameserver-policy: {\"rule-set:external\": [192.0.2.2]}\nproxies: [{name: local, type: direct}]\n", address)
	d, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	previous := saveDNS()
	defer previous.restore()
	singleton.mu.Lock()
	singleton.probeContext = context.Background()
	singleton.mu.Unlock()
	defer func() {
		singleton.mu.Lock()
		singleton.probeContext = nil
		singleton.mu.Unlock()
	}()
	physical := []mihomoDNS.NameServer{{Net: "udp", Addr: "192.0.2.53:53"}}
	if err := installAndroidProbeDNS(d, physical, []any{"192.0.2.53:53"}, false); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	ips, err := resolver.ProxyServerHostResolver.LookupIPv4(ctx, "node.example")
	if err != nil || len(ips) != 1 || ips[0] != netip.MustParseAddr("127.0.0.9") {
		t.Fatalf("outbound DNS did not use subscription upstream: ips=%v err=%v", ips, err)
	}
}

func TestStandaloneProbeResolvesOutboundWithSubscriptionDNS(t *testing.T) {
	stopTest(t)
	udp, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	dnsServer := &D.Server{PacketConn: udp, Handler: D.HandlerFunc(func(w D.ResponseWriter, r *D.Msg) {
		response := new(D.Msg)
		response.SetReply(r)
		for _, question := range r.Question {
			if question.Qtype == D.TypeA && question.Name == "node.nimbo.test." {
				response.Answer = append(response.Answer, &D.A{Hdr: D.RR_Header{Name: question.Name, Rrtype: D.TypeA, Class: D.ClassINET, Ttl: 60}, A: net.IPv4(127, 0, 0, 1)})
			}
		}
		_ = w.WriteMsg(response)
	})}
	go func() { _ = dnsServer.ActivateAndServe() }()
	defer dnsServer.Shutdown()
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Errorf("unexpected method: %s", r.Method)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer target.Close()
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodConnect {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		destination, err := net.Dial("tcp", r.Host)
		if err != nil {
			w.WriteHeader(http.StatusBadGateway)
			return
		}
		defer destination.Close()
		client, _, err := w.(http.Hijacker).Hijack()
		if err != nil {
			return
		}
		defer client.Close()
		_, _ = client.Write([]byte("HTTP/1.1 200 Connection Established\r\n\r\n"))
		go io.Copy(destination, client)
		_, _ = io.Copy(client, destination)
	}))
	defer proxy.Close()
	port := proxy.Listener.Addr().(*net.TCPAddr).Port
	source := fmt.Sprintf("dns:\n  enable: true\n  nameserver: [192.0.2.1]\n  proxy-server-nameserver: [%q]\nproxies:\n  - {name: named, type: http, server: node.nimbo.test, port: %d}\n", udp.LocalAddr().String(), port)
	reply := call(t, "probeAndroid", map[string]any{"yaml": source, "name": "named", "url": target.URL, "timeoutMs": 4000,
		"expectedStatus": "200-299", "options": map[string]any{"androidSystemDNS": []string{"192.0.2.53"}}})
	requireOK(t, reply)
}

func TestStandaloneProbeUsesProtocolWithoutStartingVPN(t *testing.T) {
	stopTest(t)
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer proxy.Close()
	source := "dns: {enable: true, nameserver: [1.1.1.1]}\n" + simpleConfig
	before := singleton.generation
	oldDNS := resolver.DefaultResolver
	reply := call(t, "probeAndroid", map[string]any{"yaml": source, "name": "local", "url": proxy.URL, "timeoutMs": 2000, "expectedStatus": "200-299", "options": map[string]any{"androidSystemDNS": []string{"192.0.2.53"}}})
	requireOK(t, reply)
	var data struct {
		VPN   bool   `json:"vpnStarted"`
		Scope string `json:"scope"`
	}
	json.Unmarshal(reply.Data, &data)
	if data.VPN || data.Scope != "standalone-outbound" || singleton.state != "stopped" || singleton.session != nil || singleton.generation != before || resolver.DefaultResolver != oldDNS {
		t.Fatal("probe changed runtime ownership")
	}
}
func TestStandaloneProbeRejectsNonSuccessStatus(t *testing.T) {
	stopTest(t)
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
	}))
	defer target.Close()
	fields := map[string]any{"yaml": "dns: {enable: true, nameserver: [1.1.1.1]}\n" + simpleConfig,
		"name": "local", "url": target.URL, "timeoutMs": 2000, "expectedStatus": "200-299",
		"options": map[string]any{"androidSystemDNS": []string{"192.0.2.53"}}}
	reply := call(t, "probeAndroid", fields)
	if reply.Success || reply.Error.Code != "PROBE_HTTP_STATUS" || singleton.state != "stopped" {
		t.Fatal("non-success endpoint must not be reported healthy")
	}
}
func TestStandaloneProbeGetsThroughNamedOutbound(t *testing.T) {
	stopTest(t)
	var connected, requested atomic.Bool
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodGet {
			requested.Store(true)
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer target.Close()
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodConnect {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		connected.Store(true)
		destination, err := net.Dial("tcp", r.Host)
		if err != nil {
			w.WriteHeader(http.StatusBadGateway)
			return
		}
		defer destination.Close()
		client, _, err := w.(http.Hijacker).Hijack()
		if err != nil {
			return
		}
		defer client.Close()
		_, _ = client.Write([]byte("HTTP/1.1 200 Connection Established\r\n\r\n"))
		go io.Copy(destination, client)
		_, _ = io.Copy(client, destination)
	}))
	defer proxy.Close()
	proxyAddress, err := net.ResolveTCPAddr("tcp", proxy.Listener.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	source := fmt.Sprintf("dns: {enable: true, nameserver: [1.1.1.1]}\nmode: rule\nproxies:\n  - {name: local, type: http, server: 127.0.0.1, port: %d}\nproxy-groups:\n  - {name: Choice, type: select, proxies: [local]}\nrules: [MATCH,Choice]\n", proxyAddress.Port)
	reply := call(t, "probeAndroid", map[string]any{"yaml": source, "name": "local", "url": target.URL,
		"timeoutMs": 2000, "expectedStatus": "200-299", "options": map[string]any{"androidSystemDNS": []string{"192.0.2.53"}}})
	requireOK(t, reply)
	if !connected.Load() || !requested.Load() || singleton.state != "stopped" {
		t.Fatal("GET did not traverse the named proxy outbound")
	}
}
func TestStandaloneProbeRejectsMissingMemberAndInvalidDNS(t *testing.T) {
	stopTest(t)
	fields := map[string]any{"yaml": "dns: {enable: true, nameserver: [1.1.1.1]}\n" + simpleConfig, "name": "Choice", "url": "http://192.0.2.2/", "timeoutMs": 1000, "options": map[string]any{"androidSystemDNS": []string{"192.0.2.53"}}}
	r := call(t, "probeAndroid", fields)
	if r.Success || r.Error.Code != "PROBE_REQUIRES_SESSION" {
		t.Fatal("group should require live graph")
	}
	fields["name"] = "local"
	fields["options"] = map[string]any{"androidSystemDNS": []string{"invalid"}}
	r = call(t, "probeAndroid", fields)
	if r.Success || r.Error.Code != "INVALID_ANDROID_DNS" {
		t.Fatal("invalid DNS admitted")
	}
	singleton.mu.Lock()
	singleton.state = "starting"
	singleton.mu.Unlock()
	defer func() { singleton.mu.Lock(); singleton.state = "stopped"; singleton.mu.Unlock() }()
	r = call(t, "probeAndroid", fields)
	if r.Success || r.Error.Code != "BUSY" {
		t.Fatal("probe raced start")
	}
}
func TestStandaloneProbeCancellationRestoresGlobals(t *testing.T) {
	stopTest(t)
	started := make(chan struct{})
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { close(started); <-r.Context().Done() }))
	defer server.Close()
	fields := map[string]any{"yaml": "dns: {enable: true, nameserver: [1.1.1.1]}\n" + simpleConfig, "name": "local", "url": server.URL, "timeoutMs": 10000, "expectedStatus": "204", "options": map[string]any{"androidSystemDNS": []string{"192.0.2.53"}}}
	done := make(chan testReply, 1)
	go func() { done <- call(t, "probeAndroid", fields) }()
	select {
	case <-started:
	case r := <-done:
		t.Fatalf("probe exited early: %v", r.Error)
	case <-time.After(3 * time.Second):
		t.Fatal("probe never started")
	}
	requireOK(t, call(t, "cancel", map[string]any{"targetRequestId": "test-probeAndroid"}))
	select {
	case r := <-done:
		if r.Success || r.Error.Code != "PROBE_CANCELLED" {
			t.Fatal("cancel did not stop probe")
		}
	case <-time.After(3 * time.Second):
		t.Fatal("cancel blocked")
	}
	if singleton.probeCancel != nil || singleton.state != "stopped" {
		t.Fatal("probe resources retained")
	}
}

func TestStandaloneProbeDNSRoutingHintsNeedNoActiveGroup(t *testing.T) {
	stopTest(t)
	var calls atomic.Int32
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { calls.Add(1); w.WriteHeader(204) }))
	defer target.Close()
	source := "dns:\n  enable: true\n  nameserver: [\"https://192.0.2.1/dns-query#Fixture group&h3=true\"]\n  default-nameserver: [\"https://192.0.2.1/dns-query#Fixture group\"]\n  proxy-server-nameserver: [\"https://192.0.2.1/dns-query#Fixture group\"]\n" + simpleConfig
	reply := call(t, "probeAndroid", map[string]any{"yaml": source, "name": "local", "url": target.URL, "timeoutMs": 2000, "expectedStatus": "200-299", "options": map[string]any{"androidSystemDNS": []string{"192.0.2.53"}}})
	requireOK(t, reply)
	if calls.Load() != 1 || singleton.state != "stopped" || singleton.session != nil {
		t.Fatal("qualified DNS prevented independent Android probe or started VPN")
	}
}
