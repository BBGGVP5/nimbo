package mihomocore

import (
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/metacubex/mihomo/component/resolver"
)

func TestDesktopProbeOutboundAndGroupsWithoutVPN(t *testing.T) {
	stopTest(t)
	var relay, gets atomic.Int32
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			t.Error("not GET")
		}
		gets.Add(1)
		w.WriteHeader(204)
	}))
	defer target.Close()
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "CONNECT" {
			w.WriteHeader(405)
			return
		}
		relay.Add(1)
		upstream, err := net.Dial("tcp", r.Host)
		if err != nil {
			w.WriteHeader(502)
			return
		}
		defer upstream.Close()
		client, _, err := w.(http.Hijacker).Hijack()
		if err != nil {
			return
		}
		defer client.Close()
		_, _ = client.Write([]byte("HTTP/1.1 200 Connection Established\r\n\r\n"))
		go io.Copy(upstream, client)
		_, _ = io.Copy(client, upstream)
	}))
	defer proxy.Close()
	source := fmt.Sprintf("mixed-port: 1\ntun: {enable: true, auto-route: true}\nexternal-controller: 0.0.0.0:1\nproxy-providers: {unused: {type: http, url: http://127.0.0.1:1/private}}\nproxies: [{name: named, type: http, server: 127.0.0.1, port: %d}]\nproxy-groups: [{name: Nested, type: select, proxies: [named]}, {name: Fallback, type: fallback, proxies: [REJECT, Nested]}]\n", proxy.Listener.Addr().(*net.TCPAddr).Port)
	generation, oldDNS := singleton.generation, resolver.DefaultResolver
	for _, name := range []string{"named", "Fallback", "DIRECT"} {
		reply := call(t, "probeDesktop", map[string]any{"yaml": source, "name": name, "url": target.URL, "timeoutMs": 2000, "expectedStatus": "200-299"})
		requireOK(t, reply)
		var data struct {
			Delay  uint16 `json:"delayMs"`
			Digest string `json:"sourceSHA256"`
			Scope  string `json:"scope"`
			VPN    bool   `json:"vpnStarted"`
		}
		_ = json.Unmarshal(reply.Data, &data)
		doc, _ := inspect(source)
		if data.Digest != doc.SourceSHA256 || data.Scope != "desktop-offline-probe" || data.VPN {
			t.Fatal("bad offline proof")
		}
		if singleton.state != "stopped" || singleton.session != nil || singleton.generation != generation || singleton.probeContext != nil || resolver.DefaultResolver != oldDNS {
			t.Fatal("probe changed ownership/globals")
		}
	}
	if relay.Load() != 2 || gets.Load() != 3 {
		t.Fatal("probe bypassed named outbound or failed to resolve group")
	}
}

func TestDesktopProbeInvalidOrDynamicGraphDoesNotDial(t *testing.T) {
	stopTest(t)
	for _, source := range []string{
		"proxy-groups: [{name: A, type: select, proxies: [A]}]",
		"proxies: [{name: A, type: direct}, {name: A, type: direct}]",
		"proxy-groups: [{name: A, type: select, use: [remote]}]",
		"proxies: [{name: A, type: direct, dialer-proxy: other}]",
		"proxies: [{name: A, type: rematch}]",
	} {
		reply := call(t, "probeDesktop", map[string]any{"yaml": source, "name": "A", "url": "http://127.0.0.1:1/", "timeoutMs": 500})
		if reply.Success || singleton.state != "stopped" || singleton.probeContext != nil {
			t.Fatal("unsafe projection admitted")
		}
	}
	reply := call(t, "probeDesktop", map[string]any{"yaml": simpleConfig, "name": "missing", "url": "http://127.0.0.1:1/", "timeoutMs": 500})
	if reply.Success || reply.Error.Code != "NOT_FOUND" {
		t.Fatal("missing proxy silently substituted")
	}
}

func TestDesktopProbeTimeoutAndCancel(t *testing.T) {
	stopTest(t)
	started := make(chan struct{}, 2)
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { started <- struct{}{}; <-r.Context().Done() }))
	defer target.Close()
	fields := map[string]any{"yaml": simpleConfig, "name": "local", "url": target.URL, "timeoutMs": 100}
	reply := call(t, "probeDesktop", fields)
	if reply.Success || reply.Error.Code != "PROBE_TIMEOUT" || strings.Contains(reply.Error.Message, target.URL) {
		t.Fatal("unsafe timeout")
	}
	<-started
	fields["timeoutMs"] = 30000
	done := make(chan testReply, 1)
	go func() { done <- call(t, "probeDesktop", fields) }()
	select {
	case <-started:
	case <-time.After(3 * time.Second):
		t.Fatal("probe never started")
	}
	requireOK(t, call(t, "cancel", map[string]any{"targetRequestId": "test-probeDesktop"}))
	select {
	case reply = <-done:
		if reply.Success || reply.Error.Code != "PROBE_CANCELLED" {
			t.Fatal("cancel failed")
		}
	case <-time.After(3 * time.Second):
		t.Fatal("cancel blocked")
	}
	if singleton.probeContext != nil || singleton.probeCancel != nil || singleton.state != "stopped" {
		t.Fatal("probe leaked")
	}
}
