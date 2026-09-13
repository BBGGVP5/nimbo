package main

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/xtls/libxray/share"
	"github.com/xtls/libxray/xray"
	xrayNet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/session"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf"
)

const diagnosticMaxRequest = 2 * 1024 * 1024
const diagnosticTag = "nimbo-diagnostic-proxy"

type diagnosticRequest struct {
	APIVersion int    `json:"apiVersion"`
	RequestID  string `json:"requestID"`
	ServerID   string `json:"serverID"`
	Config     string `json:"config"`
	Format     string `json:"format"`
	URL        string `json:"url"`
	Method     string `json:"method"`
	TimeoutMs  int    `json:"timeoutMs"`
}

type diagnosticResult struct {
	OK        bool   `json:"ok"`
	RequestID string `json:"requestID"`
	ServerID  string `json:"serverID"`
	Latency   int64  `json:"latency"`
	Error     string `json:"error,omitempty"`
}

type diagnosticCancelResult struct {
	OK        bool   `json:"ok"`
	RequestID string `json:"requestID"`
	Cancelled bool   `json:"cancelled"`
	Error     string `json:"error,omitempty"`
}

// Xray has process globals even for core.New instances. Do not run independent
// diagnostics concurrently. The slot is released only after core.Close returns.
var diagnostics = struct {
	sync.Mutex
	activeID string
	cancel   context.CancelFunc
	recent   map[string]time.Time
}{recent: make(map[string]time.Time)}

func diagnosticJSON(value any) string {
	data, err := json.Marshal(value)
	if err != nil {
		return `{"ok":false,"latency":-1,"error":"DIAGNOSTIC_INTERNAL"}`
	}
	return string(data)
}

func diagnosticKey(id string) string {
	parsed, err := uuid.Parse(id)
	if len(id) != 36 || err != nil || parsed == uuid.Nil {
		return ""
	}
	return parsed.String()
}

// Bounded tombstones cover cancel-before-Run and prevent immediate UUID reuse.
// Callers generate a new UUID for every attempt, including retries.
func diagnosticRemember(key string) {
	now := time.Now()
	for id, stamp := range diagnostics.recent {
		if now.Sub(stamp) > 2*time.Minute {
			delete(diagnostics.recent, id)
		}
	}
	if len(diagnostics.recent) >= 256 {
		var oldest string
		var stamp time.Time
		for id, t := range diagnostics.recent {
			if oldest == "" || t.Before(stamp) {
				oldest, stamp = id, t
			}
		}
		delete(diagnostics.recent, oldest)
	}
	diagnostics.recent[key] = now
}

func cancelDiagnosticJSON(text string) diagnosticCancelResult {
	var request struct {
		APIVersion int    `json:"apiVersion"`
		RequestID  string `json:"requestID"`
	}
	result := diagnosticCancelResult{}
	if len(text) > 4096 || json.Unmarshal([]byte(text), &request) != nil || request.APIVersion != 1 || diagnosticKey(request.RequestID) == "" {
		result.Error = "DIAGNOSTIC_REQUEST"
		return result
	}
	result.OK, result.RequestID = true, request.RequestID
	key := diagnosticKey(request.RequestID)
	diagnostics.Lock()
	defer diagnostics.Unlock()
	if diagnostics.activeID == key {
		diagnostics.cancel()
		result.Cancelled = true
	}
	diagnosticRemember(key)
	return result
}

func runDiagnosticJSON(text string, extension bool) (result diagnosticResult) {
	started := time.Now()
	result.Latency = -1
	var request diagnosticRequest
	if len(text) > diagnosticMaxRequest || json.Unmarshal([]byte(text), &request) != nil {
		result.Error = "DIAGNOSTIC_REQUEST"
		return
	}
	key := diagnosticKey(request.RequestID)
	if request.APIVersion != 1 || key == "" || len(request.ServerID) == 0 || len(request.ServerID) > 512 ||
		len(request.Config) == 0 || len(request.Config) > 1024*1024 || request.TimeoutMs < 1 || request.TimeoutMs > 60000 ||
		(request.Method != "GET" && request.Method != "HEAD") || len(request.URL) > 4096 {
		result.Error = "DIAGNOSTIC_REQUEST"
		return
	}
	result.RequestID, result.ServerID = request.RequestID, request.ServerID
	if extension {
		result.Error = "DIAGNOSTIC_APP_ONLY"
		return
	}
	target, err := url.ParseRequestURI(request.URL)
	if err != nil || target.Hostname() == "" || target.User != nil || target.Fragment != "" || (target.Scheme != "http" && target.Scheme != "https") {
		result.Error = "DIAGNOSTIC_REQUEST"
		return
	}
	ctx, cancel := context.WithDeadline(context.Background(), started.Add(time.Duration(request.TimeoutMs)*time.Millisecond))
	defer cancel()
	diagnostics.Lock()
	if _, found := diagnostics.recent[key]; found {
		diagnostics.Unlock()
		result.Error = "DIAGNOSTIC_CANCELLED"
		return
	}
	if diagnostics.activeID != "" {
		diagnostics.Unlock()
		result.Error = "DIAGNOSTIC_BUSY"
		return
	}
	diagnostics.activeID, diagnostics.cancel = key, cancel
	diagnostics.Unlock()
	defer func() {
		diagnostics.Lock()
		diagnostics.activeID, diagnostics.cancel = "", nil
		diagnosticRemember(key)
		diagnostics.Unlock()
	}()
	// An app-process integration must never start a managed tunnel. Refuse
	// accidental use in a managed-core process before building any new instance.
	if xray.GetXrayState() {
		result.Error = "DIAGNOSTIC_MANAGED_ACTIVE"
		return
	}
	result.Latency, result.Error = executeDiagnostic(ctx, request)
	if ctx.Err() != nil {
		result.Error = "DIAGNOSTIC_CANCELLED"
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			result.Error = "DIAGNOSTIC_TIMEOUT"
		}
	}
	if result.Error == "" {
		result.OK = true
	} else {
		result.Latency = -1
	}
	return
}

func diagnosticOutbound(request diagnosticRequest) (conf.OutboundDetourConfig, string) {
	var empty conf.OutboundDetourConfig
	raw := []byte(request.Config)
	switch request.Format {
	case "awg":
		return empty, "DIAGNOSTIC_UNSUPPORTED"
	case "share":
		// A single server only: do not silently skip invalid subscription entries
		// and accidentally measure a different server selected by the converter.
		text := strings.TrimSpace(request.Config)
		if strings.ContainsAny(text, "\r\n") || strings.HasPrefix(text, "{") {
			return empty, "DIAGNOSTIC_CONFIG"
		}
		link, err := url.Parse(text)
		if err != nil || link.Scheme == "" {
			return empty, "DIAGNOSTIC_CONFIG"
		}
		raw, err = share.ConvertShareLinksToXrayJson(text, "")
		if err != nil {
			return empty, "DIAGNOSTIC_CONFIG"
		}
	case "xray":
	default:
		return empty, "DIAGNOSTIC_UNSUPPORTED"
	}
	var document struct {
		Outbounds []json.RawMessage `json:"outbounds"`
	}
	if json.Unmarshal(raw, &document) != nil || len(document.Outbounds) == 0 || len(document.Outbounds) > 64 {
		return empty, "DIAGNOSTIC_CONFIG"
	}
	var selected []conf.OutboundDetourConfig
	for _, rawOutbound := range document.Outbounds {
		var outbound conf.OutboundDetourConfig
		if json.Unmarshal(rawOutbound, &outbound) != nil {
			return empty, "DIAGNOSTIC_CONFIG"
		}
		switch outbound.Protocol {
		case "freedom", "blackhole", "dns":
			continue
		case "vmess", "vless", "trojan", "shadowsocks", "socks", "http":
		default:
			return empty, "DIAGNOSTIC_UNSUPPORTED"
		}
		var fields any
		if json.Unmarshal(rawOutbound, &fields) != nil || diagnosticUnsafeFields(fields) || outbound.ProxySettings != nil {
			return empty, "DIAGNOSTIC_UNSAFE_ROUTE"
		}
		// sendThrough is a native source bind address in pinned libXray. We
		// cannot promise equivalent app-process binding, so reject it explicitly.
		if outbound.SendThrough != nil && *outbound.SendThrough != "" {
			return empty, "DIAGNOSTIC_UNSUPPORTED_BIND"
		}
		outbound.Tag = diagnosticTag
		selected = append(selected, outbound)
	}
	if len(selected) != 1 {
		return empty, "DIAGNOSTIC_AMBIGUOUS_ROUTE"
	}
	return selected[0], ""
}

func diagnosticUnsafeFields(value any) bool {
	switch v := value.(type) {
	case map[string]any:
		for key, child := range v {
			switch strings.ToLower(key) {
			case "certificatefile", "keyfile", "masterkeylog", "secretslog", "dialerproxy", "interface":
				if child != nil && child != "" {
					return true
				}
			}
			if diagnosticUnsafeFields(child) {
				return true
			}
		}
	case []any:
		for _, child := range v {
			if diagnosticUnsafeFields(child) {
				return true
			}
		}
	}
	return false
}

type diagnosticConnections struct {
	sync.Mutex
	closed bool
	items  []net.Conn
	dials  sync.WaitGroup
}

func (c *diagnosticConnections) close() {
	c.Lock()
	defer c.Unlock()
	c.closed = true
	for _, connection := range c.items {
		_ = connection.Close()
	}
	c.items = nil
}

func executeDiagnostic(ctx context.Context, request diagnosticRequest) (int64, string) {
	outbound, code := diagnosticOutbound(request)
	if code != "" {
		return -1, code
	}
	if ctx.Err() != nil {
		return -1, "DIAGNOSTIC_CANCELLED"
	}
	// Build a fresh projection, never the user's root Config: Env, TUN,
	// listeners, DNS, routing, metrics and logging cannot affect this process.
	config, err := (&conf.Config{OutboundConfigs: []conf.OutboundDetourConfig{outbound},
		LogConfig: &conf.LogConfig{LogLevel: "none"}}).Build()
	if err != nil {
		return -1, "DIAGNOSTIC_CONFIG"
	}
	if ctx.Err() != nil {
		return -1, "DIAGNOSTIC_CANCELLED"
	}
	server, err := core.New(config)
	if err != nil {
		return -1, "DIAGNOSTIC_SETUP"
	}
	defer server.Close()
	if ctx.Err() != nil {
		return -1, "DIAGNOSTIC_CANCELLED"
	}
	if err := server.Start(); err != nil {
		return -1, "DIAGNOSTIC_SETUP"
	}
	connections := &diagnosticConnections{}
	defer func() {
		connections.close()
		connections.dials.Wait()
	}()
	stopCancel := context.AfterFunc(ctx, connections.close)
	defer stopCancel()
	transport := &http.Transport{DisableKeepAlives: true, MaxResponseHeaderBytes: 32 * 1024,
		DialContext: func(dialContext context.Context, network, address string) (net.Conn, error) {
			connections.Lock()
			if connections.closed || ctx.Err() != nil {
				connections.Unlock()
				return nil, context.Canceled
			}
			connections.dials.Add(1)
			connections.Unlock()
			defer connections.dials.Done()
			if network != "tcp" && network != "tcp4" && network != "tcp6" {
				return nil, errors.New("unsupported diagnostic network")
			}
			destination, err := xrayNet.ParseDestination("tcp:" + address)
			if err != nil {
				return nil, err
			}
			// Transport may detach its dial context from the request; the whole
			// diagnostic deadline is authoritative, including DNS/setup/dial.
			forced := session.SetForcedOutboundTagToContext(ctx, diagnosticTag)
			connection, err := core.Dial(forced, server, destination)
			if err != nil {
				return nil, err
			}
			connections.Lock()
			defer connections.Unlock()
			if connections.closed || ctx.Err() != nil {
				_ = connection.Close()
				return nil, context.Canceled
			}
			connections.items = append(connections.items, connection)
			return connection, nil
		}}
	defer transport.CloseIdleConnections()
	client := &http.Client{Transport: transport, CheckRedirect: func(*http.Request, []*http.Request) error {
		return http.ErrUseLastResponse
	}}
	httpRequest, err := http.NewRequestWithContext(ctx, request.Method, request.URL, nil)
	if err != nil {
		return -1, "DIAGNOSTIC_REQUEST"
	}
	httpRequest.Header.Set("User-Agent", "Nimbo-Diagnostic/1")
	requestStarted := time.Now()
	response, err := client.Do(httpRequest)
	latency := time.Since(requestStarted).Milliseconds()
	if err != nil {
		return -1, "DIAGNOSTIC_NETWORK"
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return -1, "DIAGNOSTIC_HTTP_STATUS"
	}
	// Measure response headers; do not buffer or follow a response body.
	return latency, ""
}
