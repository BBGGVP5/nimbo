package main

import (
	"bufio"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"sync"
	"sync/atomic"
	"testing"

	"github.com/google/uuid"
)

func diagnosticFixture() diagnosticRequest {
	return diagnosticRequest{APIVersion: 1, RequestID: uuid.NewString(), ServerID: "fixture",
		Config: `{"outbounds":[{"protocol":"http","settings":{"servers":[{"address":"127.0.0.1","port":9}]}}]}`,
		Format: "xray", URL: "https://example.invalid/", Method: "GET", TimeoutMs: 1000}
}

func TestDiagnosticProjection(t *testing.T) {
	request := diagnosticFixture()
	request.Config = `{"env":{"NIMBO_SHOULD_NOT_SET":"value"},"inbounds":[{"protocol":"tun"}],"outbounds":[{"protocol":"freedom"},{"protocol":"http","tag":"selected","mux":{"enabled":true,"concurrency":2},"settings":{"servers":[{"address":"127.0.0.1","port":9}]}}]}`
	outbound, code := diagnosticOutbound(request)
	if code != "" || outbound.Tag != diagnosticTag || outbound.Protocol != "http" || outbound.SendThrough != nil {
		t.Fatalf("projection failed: %s", code)
	}
	if outbound.MuxSettings == nil || !outbound.MuxSettings.Enabled || outbound.MuxSettings.Concurrency != 2 {
		t.Fatal("proxy settings were silently changed")
	}
	for _, raw := range []string{
		`{"outbounds":[{"protocol":"freedom"}]}`,
		`{"outbounds":[{"protocol":"http"},{"protocol":"socks"}]}`,
		`{"outbounds":[{"protocol":"http","streamSettings":{"sockopt":{"dialerProxy":"direct"}}}]}`,
		`{"outbounds":[{"protocol":"http","streamSettings":{"tlsSettings":{"certificates":[{"certificateFile":"/tmp/key"}]}}}]}`,
		`{"outbounds":[{"protocol":"http","sendThrough":"127.0.0.1"}]}`,
	} {
		request.Config = raw
		if _, code := diagnosticOutbound(request); code == "" {
			t.Fatal("unsafe/ambiguous config accepted")
		}
	}
}

func TestDiagnosticValidationAndExtension(t *testing.T) {
	request := diagnosticFixture()
	result := runDiagnosticJSON(diagnosticJSON(request), true)
	if result.Error != "DIAGNOSTIC_APP_ONLY" || result.RequestID != request.RequestID {
		t.Fatal(result.Error)
	}
	request.Method = "POST"
	if result := runDiagnosticJSON(diagnosticJSON(request), false); result.Error != "DIAGNOSTIC_REQUEST" {
		t.Fatal(result.Error)
	}
	request = diagnosticFixture()
	request.Format = "awg"
	if result := runDiagnosticJSON(diagnosticJSON(request), false); result.Error != "DIAGNOSTIC_UNSUPPORTED" {
		t.Fatal(result.Error)
	}
}

func TestDiagnosticCancelBeforeStartAndIdentity(t *testing.T) {
	request := diagnosticFixture()
	cancelRequest, _ := json.Marshal(map[string]any{"apiVersion": 1, "requestID": request.RequestID})
	if result := cancelDiagnosticJSON(string(cancelRequest)); !result.OK {
		t.Fatal(result.Error)
	}
	result := runDiagnosticJSON(diagnosticJSON(request), false)
	if result.Error != "DIAGNOSTIC_CANCELLED" || result.Latency != -1 || result.RequestID != request.RequestID || result.ServerID != request.ServerID {
		t.Fatal("cancellation or correlation lost")
	}
	diagnostics.Lock()
	for range 300 {
		diagnosticRemember(uuid.NewString())
	}
	count := len(diagnostics.recent)
	diagnostics.Unlock()
	if count > 256 {
		t.Fatal("unbounded cancellation IDs")
	}
}

func TestDiagnosticAlreadyCancelledSetup(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, code := executeDiagnostic(ctx, diagnosticFixture()); code != "DIAGNOSTIC_CANCELLED" {
		t.Fatal(code)
	}
}

func TestDiagnosticCancelConcurrentIDs(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	id := uuid.NewString()
	diagnostics.Lock()
	diagnostics.activeID, diagnostics.cancel = id, cancel
	diagnostics.Unlock()
	defer func() {
		diagnostics.Lock()
		diagnostics.activeID, diagnostics.cancel = "", nil
		diagnostics.Unlock()
	}()
	var workers sync.WaitGroup
	for i := range 32 {
		workers.Add(1)
		go func(correct bool) {
			defer workers.Done()
			requestID := uuid.NewString()
			if correct {
				requestID = id
			}
			result := cancelDiagnosticJSON(diagnosticJSON(map[string]any{"apiVersion": 1, "requestID": requestID}))
			if !result.OK || result.Cancelled != correct {
				t.Error("request cancellation crossed IDs")
			}
		}(i%2 == 0)
	}
	workers.Wait()
	if ctx.Err() == nil {
		t.Fatal("matching cancellation did not signal request")
	}
}

// Real app-process Xray dispatch against a loopback CONNECT fixture. The target
// hostname cannot resolve locally; success therefore proves forced proxy use.
func TestDiagnosticRealHTTPRoute(t *testing.T) {
	var hits atomic.Int32
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "CONNECT" {
			t.Error("expected proxy CONNECT")
			w.WriteHeader(405)
			return
		}
		conn, reader, err := w.(http.Hijacker).Hijack()
		if err != nil {
			t.Error(err)
			return
		}
		defer conn.Close()
		reader.WriteString("HTTP/1.1 200 Connection established\r\n\r\n")
		reader.Flush()
		request, err := http.ReadRequest(bufio.NewReader(reader))
		if err != nil {
			return
		}
		if request.Method != "GET" || request.URL.Path != "/probe" {
			t.Error("wrong probe request")
			return
		}
		hits.Add(1)
		reader.WriteString("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
		reader.Flush()
	}))
	defer proxy.Close()
	endpoint, _ := url.Parse(proxy.URL)
	port, _ := strconv.Atoi(endpoint.Port())
	request := diagnosticFixture()
	request.URL = "http://no-direct-probe.invalid/probe"
	request.TimeoutMs = 2000
	request.Config = diagnosticJSON(map[string]any{"outbounds": []any{
		map[string]any{"tag": "direct", "protocol": "freedom"},
		map[string]any{"tag": "node", "protocol": "http", "settings": map[string]any{"servers": []any{map[string]any{"address": "127.0.0.1", "port": port}}}},
	}, "routing": map[string]any{"rules": []any{map[string]any{"outboundTag": "direct"}}}})
	result := runDiagnosticJSON(diagnosticJSON(request), false)
	if !result.OK || result.Latency < 0 || hits.Load() != 1 {
		t.Fatalf("not the selected HTTP route: %+v hits=%d", result, hits.Load())
	}
}
