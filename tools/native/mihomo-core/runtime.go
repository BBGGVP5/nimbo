// Package mihomocore embeds the pinned Mihomo core behind a versioned JSON facade.
// It never changes the host system proxy, routes, DNS configuration or firewall.
package mihomocore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"go.yaml.in/yaml/v3"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/adapter/inbound"
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/component/geodata"
	mihomoHTTP "github.com/metacubex/mihomo/component/http"
	"github.com/metacubex/mihomo/component/keepalive"
	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/component/profile"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/component/resource"
	"github.com/metacubex/mihomo/component/sniffer"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	mihomoDNS "github.com/metacubex/mihomo/dns"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

const coreVersion = "v1.19.31"
const coreCommit = "ab405bad5beeeac8b003bb01f60f134f6df54471"

type startOptions struct {
	DataDir           string   `json:"dataDir"`
	NetworkOwner      string   `json:"networkOwner"`
	AndroidSystemDNS  []string `json:"androidSystemDNS,omitempty"`
	PacketIPv6        bool     `json:"packetIPv6,omitempty"`
	PacketSystemDNS   []string `json:"packetSystemDNS,omitempty"`
	AndroidIPv6       bool     `json:"androidIPv6,omitempty"`
	MixedAddress      string   `json:"mixedAddress"`
	ControllerAddress string   `json:"controllerAddress"`
	Secret            string   `json:"secret"`
	StartupTimeoutMs  int      `json:"startupTimeoutMs,omitempty"`
}
type request struct {
	APIVersion         int          `json:"apiVersion"`
	RequestID          string       `json:"requestId"`
	Operation          string       `json:"operation"`
	Generation         *uint64      `json:"generation,omitempty"`
	YAML               string       `json:"yaml,omitempty"`
	Options            startOptions `json:"options,omitempty"`
	Group              string       `json:"group,omitempty"`
	Name               string       `json:"name,omitempty"`
	URL                string       `json:"url,omitempty"`
	TimeoutMs          int          `json:"timeoutMs,omitempty"`
	ExpectedStatus     string       `json:"expectedStatus,omitempty"`
	TargetRequestID    string       `json:"targetRequestId,omitempty"`
	responseGeneration uint64
	packetOwner        bool   // trusted packet-flow entry; never decoded from JSON
	borrowedFD         *int64 // trusted local entry only; never decoded from JSON
}
type response struct {
	APIVersion int    `json:"apiVersion"`
	RequestID  string `json:"requestId"`
	Success    bool   `json:"success"`
	Generation uint64 `json:"generation"`
	Data       any    `json:"data,omitempty"`
	Error      *issue `json:"error,omitempty"`
}
type runtimeStatus struct {
	State             string `json:"state"`
	MixedAddress      string `json:"mixedAddress"`
	ControllerAddress string `json:"controllerAddress"`
	NetworkOwner      string `json:"networkOwner"`
	SourceSHA256      string `json:"sourceSHA256"`
	CoreVersion       string `json:"coreVersion"`
	CoreCommit        string `json:"coreCommit"`
	APIVersion        int    `json:"apiVersion"`
	TunReady          bool   `json:"tunReady,omitempty"`
}

type capabilitySet struct {
	DesktopProxy bool `json:"desktopProxy"`
	AndroidVPN   bool `json:"androidVpn"`
	IOSVPN       bool `json:"iosVpn"`
	RuleRouting  bool `json:"ruleRouting"`
	IPv4         bool `json:"ipv4"`
	IPv6         bool `json:"ipv6"`
	TCP          bool `json:"tcp"`
	UDP          bool `json:"udp"`
	DNS          bool `json:"dns"`
	Providers    bool `json:"providers"`
	ProviderAuto bool `json:"providerAutoRefresh"`
	GroupSelect  bool `json:"groupSelection"`
	GroupAuto    bool `json:"automaticGroups"`
	HealthChecks bool `json:"healthChecks"`
	Counters     bool `json:"counters"`
}
type session struct {
	cfg                *config.Config
	doc                *inspection
	mixed              io.Closer
	accepted           *trackedListener
	controller         *controlServer
	home               string
	oldHome            string
	oldIPv6            bool
	oldMode            tunnel.TunnelMode
	oldProcess         process.FindProcessMode
	oldStoreSelected   bool
	oldLogLevel        log.LogLevel
	oldDNS             dnsState
	oldAndroidGlobals  androidGlobalState
	ctx                context.Context
	cancel             context.CancelFunc
	watchDone          chan struct{}
	closeProvidersOnce sync.Once
	cacheOpened        bool
	mobile             *mobileSession
	packet             ownedPacketFlow
	tun                io.Closer
}
type manager struct {
	probeRequestID       string
	probeCancel          context.CancelFunc
	probeContext         context.Context
	mu                   sync.Mutex
	op                   sync.Mutex
	generation           uint64
	state                string
	cancel               context.CancelFunc
	session              *session
	info                 runtimeStatus
	startRequestID       string
	cancelledStarts      map[string]time.Time
	lastDiagnosticConfig map[string]any
}

var singleton = manager{state: "stopped"}

func capabilities() capabilitySet {
	mobileFeatures := androidTunCompiled || (packetFlowCompiled && runtime.GOOS == "ios")
	return capabilitySet{
		DesktopProxy: true,
		AndroidVPN:   androidTunCompiled,
		IOSVPN:       packetFlowCompiled && runtime.GOOS == "ios",
		RuleRouting:  mobileFeatures,
		IPv4:         true,
		IPv6:         mobileFeatures, // The Android Builder and Mihomo TUN share an explicit dual-stack opt-in.
		TCP:          true,
		UDP:          mobileFeatures,
		DNS:          mobileFeatures,
		Providers:    true,
		ProviderAuto: true,
		GroupSelect:  true,
		GroupAuto:    true,
		HealthChecks: true,
		Counters:     false,
	}
}

// Invoke is deliberately string-only for gomobile and C ABI wrappers. Native
// operations are serialized; status and stop cancellation bypass the op lock.
func Invoke(input string) (output string) {
	return invoke(input, nil)
}

func invoke(input string, borrowedFD *int64) string {
	return invokeOwned(input, borrowedFD, false)
}

func invokeOwned(input string, borrowedFD *int64, packetOwner bool) (output string) {
	r := request{}
	resp := response{APIVersion: 1}
	singleton.mu.Lock()
	r.responseGeneration = singleton.generation
	singleton.mu.Unlock()
	defer func() {
		if recover() != nil {
			resp.Success = false
			resp.Data = nil
			resp.Error = problem("NATIVE_PANIC", "", "native operation failed; runtime must be stopped before retry")
		}
		resp.Generation = r.responseGeneration
		b, err := json.Marshal(resp)
		if err != nil {
			b = []byte(`{"apiVersion":1,"requestId":"","success":false,"generation":0,"error":{"code":"SERIALIZATION","message":"native response could not be encoded"}}`)
		}
		output = string(b)
	}()
	if len(input) > maxRequest {
		resp.Error = problem("INVALID_REQUEST", "$", "request exceeds 8 MiB")
		return
	}
	if err := strictJSON(input); err != nil {
		resp.Error = problem("INVALID_REQUEST", "$", err.Error())
		return
	}
	dec := json.NewDecoder(strings.NewReader(input))
	dec.DisallowUnknownFields()
	if err := dec.Decode(&r); err != nil {
		resp.Error = problem("INVALID_REQUEST", "$", err.Error())
		return
	}
	var tail any
	if err := dec.Decode(&tail); err != io.EOF {
		resp.Error = problem("INVALID_REQUEST", "$", "one JSON object required")
		return
	}
	resp.RequestID = r.RequestID
	r.borrowedFD = borrowedFD
	r.packetOwner = packetOwner
	if packetOwner && (r.Operation != "start" || borrowedFD != nil) {
		resp.Error = problem("INVALID_REQUEST", "operation", "packet-flow entry accepts only start")
		return
	}
	if borrowedFD != nil && r.Operation != "start" {
		resp.Error = problem("INVALID_REQUEST", "operation", "StartAndroid accepts only start")
		return
	}
	if r.APIVersion != 1 {
		resp.Error = problem("API_VERSION", "apiVersion", "expected 1")
		return
	}
	data, err := singleton.dispatch(&r)
	if err != nil {
		var typed *issue
		if errors.As(err, &typed) {
			resp.Error = typed
		} else {
			resp.Error = problem("NATIVE_ERROR", "", err.Error())
		}
		return
	}
	resp.Success = true
	resp.Data = data
	return
}

func (m *manager) statusLocked() runtimeStatus {
	s := m.info
	s.State = m.state
	if m.state == "running" && m.session != nil && (m.session.mobile != nil || m.session.packet != nil) && m.session.ctx.Err() != nil {
		s.State = "failed"
		s.TunReady = false
	}
	s.CoreVersion = coreVersion
	s.CoreCommit = coreCommit
	s.APIVersion = 1
	return s
}
func (m *manager) checkGeneration(r request) error {
	if r.Generation != nil && *r.Generation != m.generation {
		return problem("STALE_GENERATION", "generation", "runtime generation changed")
	}
	return nil
}
func (m *manager) dispatch(r *request) (any, error) {
	m.mu.Lock()
	r.responseGeneration = m.generation
	if err := m.checkGeneration(*r); err != nil {
		m.mu.Unlock()
		return nil, err
	}
	if r.Operation == "status" {
		s := m.statusLocked()
		m.mu.Unlock()
		return s, nil
	}
	if r.Operation == "cancel" {
		if r.TargetRequestID == "" || len(r.TargetRequestID) > 256 {
			m.mu.Unlock()
			return nil, problem("INVALID_REQUEST", "targetRequestId", "nonempty ID <=256 bytes required")
		}
		if m.probeRequestID == r.TargetRequestID && m.probeCancel != nil {
			m.probeCancel()
			m.mu.Unlock()
			return map[string]any{"cancelledRequestId": r.TargetRequestID}, nil
		}
		if m.cancelledStarts == nil {
			m.cancelledStarts = map[string]time.Time{}
		}
		now := time.Now()
		for id, expiry := range m.cancelledStarts {
			if now.After(expiry) {
				delete(m.cancelledStarts, id)
			}
		}
		if len(m.cancelledStarts) >= 128 {
			m.mu.Unlock()
			return nil, problem("CANCEL_LIMIT", "targetRequestId", "too many cancellation tombstones")
		}
		if m.startRequestID == r.TargetRequestID && m.state == "running" {
			m.mu.Unlock()
			return nil, problem("ALREADY_COMMITTED", "targetRequestId", "start committed; use generation-bound stop")
		}
		m.cancelledStarts[r.TargetRequestID] = now.Add(2 * time.Minute)
		if m.startRequestID == r.TargetRequestID && m.cancel != nil {
			m.cancel()
		}
		m.mu.Unlock()
		return map[string]any{"cancelledRequestId": r.TargetRequestID}, nil
	}
	if r.Operation == "stop" && m.cancel != nil {
		m.cancel()
	}
	if r.Operation == "stop" && m.probeCancel != nil {
		m.probeCancel()
	}
	m.mu.Unlock()
	if r.Operation == "capabilities" {
		return capabilities(), nil
	}
	if r.Operation == "inspect" {
		return inspect(r.YAML)
	}
	if r.Operation == "preflightIOSPacketFlow" {
		d, err := inspect(r.YAML)
		if err != nil {
			return nil, err
		}
		if err := packetRuntimePolicy(d); err != nil {
			return nil, err
		}
		if err := androidRuntimePolicy(d); err != nil {
			return nil, err
		}
		return map[string]any{"valid": true, "sourceSHA256": d.SourceSHA256, "scope": "ios-public-packet-flow", "compiled": packetFlowCompiled}, nil
	}
	if r.Operation == "preflightAndroid" {
		// Pure source admission, safe alongside a live runtime. Do not call
		// nativeParse here: constructors can mutate globals and own resources.
		d, err := inspect(r.YAML)
		if err != nil {
			return nil, err
		}
		if err := androidRuntimePolicy(d); err != nil {
			return nil, err
		}
		result := map[string]any{"valid": true, "sourceSHA256": d.SourceSHA256, "scope": "upstream-mihomo-android-vpn"}
		if tun, ok := d.root["tun"].(map[string]any); ok {
			if excluded, ok := tun["exclude-package"].([]any); ok {
				result["excludedPackages"] = excluded
			}
		}
		return result, nil
	}
	m.op.Lock()
	defer m.op.Unlock()
	defer func() { m.mu.Lock(); r.responseGeneration = m.generation; m.mu.Unlock() }()
	m.mu.Lock()
	err := m.checkGeneration(*r)
	m.mu.Unlock()
	if err != nil {
		return nil, err
	}
	switch r.Operation {
	case "diagnosticConfig":
		if m.lastDiagnosticConfig == nil {
			return map[string]any{"available": false}, nil
		}
		return m.lastDiagnosticConfig, nil
	case "probeAndroid":
		return m.probeAndroid(*r)
	case "start":
		return m.start(*r)
	case "stop":
		return m.stop()
	case "validate":
		m.mu.Lock()
		busy := m.state != "stopped"
		m.mu.Unlock()
		if busy {
			return nil, problem("BUSY", "", "validation is serialized with native globals; stop first")
		}
		d, err := inspect(r.YAML)
		if err != nil {
			return nil, err
		}
		if len(d.StrictIssues) > 0 {
			return nil, &d.StrictIssues[0]
		}
		cfg, err := nativeParse(d)
		if err != nil {
			return nil, err
		}
		defer closeConfig(cfg)
		if err = rebindProviders(cfg, d, C.Path.HomeDir()); err != nil {
			return nil, err
		}
		if err = rebindRuleProviders(cfg, d, C.Path.HomeDir()); err != nil {
			return nil, err
		}
		return map[string]any{"valid": true, "sourceSHA256": d.SourceSHA256, "scope": "managed-desktop-proxy", "providersFetched": false}, nil
	}
	m.mu.Lock()
	s := m.session
	running := m.state == "running"
	m.mu.Unlock()
	if !running || s == nil {
		return nil, problem("NOT_RUNNING", "", "start a runtime first")
	}
	mobileGraphLocked := false
	if s.mobile != nil {
		if s.ctx.Err() != nil {
			return nil, problem("NOT_RUNNING", "", "mobile session failed; stop required")
		}
		s.mobile.graph.Lock()
		mobileGraphLocked = true
	}
	defer func() {
		if mobileGraphLocked {
			s.mobile.graph.Unlock()
		}
	}()
	switch r.Operation {
	case "networkChanged":
		if s.packet == nil {
			return nil, problem("INVALID_REQUEST", "operation", "packet-flow session required")
		}
		statistic.DefaultManager.Range(func(c statistic.Tracker) bool { _ = c.Close(); return true })
		resolver.ResetConnection()
		return map[string]any{"reset": true}, nil
	case "snapshot":
		return snapshot(s)
	case "select":
		p, ok := s.cfg.Proxies[r.Group]
		if !ok {
			return nil, problem("NOT_FOUND", "group", "group not found")
		}
		if p.Type() != C.Selector {
			return nil, problem("NOT_SELECTABLE", "group", "only native select groups accept a manual selection")
		}
		selector, ok := p.Adapter().(interface {
			outboundgroup.SelectAble
			Now() string
		})
		if !ok {
			return nil, problem("NOT_SELECTABLE", "group", "native group does not support selection")
		}
		previous := selector.Now()
		connections := selectedGroupConnections(s, r.Group)
		if err := selector.Set(r.Name); err != nil {
			return nil, problem("INVALID_SELECTION", "name", err.Error())
		}
		if previous != r.Name {
			closeGroupConnections(connections)
		}
		if s.cfg.Profile.StoreSelected {
			cachefile.Cache().SetSelected(r.Group, r.Name)
		}
		return snapshot(s)
	case "autoSelect":
		if r.TimeoutMs < 1 || r.TimeoutMs > 10000 {
			return nil, problem("INVALID_REQUEST", "timeoutMs", "expected 1..10000 ms per node")
		}
		if err := checkURL(r.URL); err != nil {
			return nil, err
		}
		if r.ExpectedStatus == "" {
			return nil, problem("INVALID_REQUEST", "expectedStatus", "an explicit healthy HTTP status range is required")
		}
		expected, err := utils.NewUnsignedRanges[uint16](r.ExpectedStatus)
		if err != nil {
			return nil, problem("INVALID_REQUEST", "expectedStatus", "invalid HTTP status range")
		}
		group := s.cfg.Proxies[r.Group]
		if group == nil {
			return nil, problem("NOT_FOUND", "group", "group not found")
		}
		if group.Type() != C.Selector {
			return nil, problem("NOT_SELECTABLE", "group", "one-shot auto selection requires a manual select group")
		}
		selectable, ok := group.Adapter().(interface {
			outboundgroup.SelectAble
			Now() string
		})
		if !ok {
			return nil, problem("NOT_SELECTABLE", "group", "group does not support selection")
		}
		proxyGroup, ok := group.Adapter().(interface{ Proxies() []C.Proxy })
		if !ok {
			return nil, problem("NOT_SELECTABLE", "group", "group does not expose candidate nodes")
		}
		candidates := proxyGroup.Proxies()
		eligible := make([]C.Proxy, 0, len(candidates))
		for _, candidate := range candidates {
			if candidate == nil {
				continue
			}
			switch candidate.Type() {
			case C.Direct, C.Reject, C.RejectDrop, C.Compatible, C.Pass, C.PassRule, C.Rematch:
				continue // Never silently auto-pick a direct or synthetic route.
			default:
				eligible = append(eligible, candidate)
			}
		}
		if len(eligible) == 0 {
			return nil, problem("NO_CANDIDATE", "group", "no remote proxy nodes are available for one-shot selection")
		}
		if len(eligible) > 128 {
			return nil, problem("TOO_MANY_CANDIDATES", "group", "one-shot selection is limited to 128 nodes")
		}
		if s.mobile != nil {
			if !s.mobile.begin() {
				return nil, problem("NOT_RUNNING", "", "mobile session is stopping")
			}
			defer s.mobile.end()
			// Existing TUN traffic keeps routing during the one-shot probe. The
			// manager operation lock prevents another API call from mutating the
			// provider/group graph until selection commits below.
			s.mobile.graph.Unlock()
			mobileGraphLocked = false
		}
		probeCtx, cancel := context.WithTimeout(s.ctx, 30*time.Second)
		best, delay, probed, probeErr := oneShotBestProxy(probeCtx, eligible, r.URL, r.TimeoutMs, expected)
		cancel()
		if s.ctx.Err() != nil {
			return nil, problem("NOT_RUNNING", "", "runtime stopped during node selection")
		}
		if probeErr != nil {
			return nil, probeErr
		}
		if s.mobile != nil {
			s.mobile.graph.Lock()
			mobileGraphLocked = true
			if s.ctx.Err() != nil {
				return nil, problem("NOT_RUNNING", "", "runtime stopped before selection could commit")
			}
		}
		previous := selectable.Now()
		connections := selectedGroupConnections(s, r.Group)
		if err := selectable.Set(best.Name()); err != nil {
			return nil, problem("INVALID_SELECTION", "group", "healthy proxy disappeared before selection")
		}
		if previous != best.Name() {
			closeGroupConnections(connections)
		}
		return map[string]any{"selected": best.Name(), "delayMs": delay, "probed": probed, "oneShot": true}, nil
	case "refreshProvider":
		p, ok := s.cfg.Providers[r.Name]
		if !ok {
			return nil, problem("NOT_FOUND", "name", "provider not found")
		}
		if _, declared := s.doc.DeclaredGraph.Providers[r.Name]; !declared {
			return nil, problem("NOT_REFRESHABLE", "name", "synthetic provider cannot refresh")
		}
		if err := p.Update(); err != nil {
			return nil, problem("PROVIDER_REFRESH", "name", err.Error())
		}
		return snapshot(s)
	case "refreshRuleProvider":
		p, ok := s.cfg.RuleProviders[r.Name]
		if !ok {
			return nil, problem("NOT_FOUND", "name", "rule provider not found")
		}
		if err := p.Update(); err != nil {
			return nil, problem("PROVIDER_REFRESH", "name", err.Error())
		}
		return snapshot(s)
	case "delay", "nimboDelay":
		if r.TimeoutMs < 1 || r.TimeoutMs > 30000 {
			return nil, problem("INVALID_REQUEST", "timeoutMs", "expected 1..30000")
		}
		if err := checkURL(r.URL); err != nil {
			return nil, err
		}
		p := s.cfg.Proxies[r.Name]
		if p == nil {
			for _, pv := range s.cfg.Providers {
				for _, candidate := range pv.Proxies() {
					if candidate.Name() == r.Name {
						if p != nil && p != candidate {
							return nil, problem("AMBIGUOUS_PROXY", "name", "provider proxy name is ambiguous")
						}
						p = candidate
					}
				}
			}
		}
		if p == nil {
			return nil, problem("NOT_FOUND", "name", "proxy not found")
		}
		expected, err := utils.NewUnsignedRanges[uint16](r.ExpectedStatus)
		if err != nil {
			return nil, err
		}
		ctx, cancel := context.WithTimeout(s.ctx, time.Duration(r.TimeoutMs)*time.Millisecond)
		defer cancel()
		if r.Operation == "nimboDelay" {
			// Match standalone Nimbo Ping: a real GET through one named outbound.
			// A group can silently select another node, so reject it here.
			declared := false
			for _, mapping := range s.doc.DeclaredGraph.Proxies {
				if str(mapping, "name") == r.Name {
					declared = true
					break
				}
			}
			if !declared {
				return nil, problem("PROBE_REQUIRES_SESSION", "name", "select a concrete node")
			}
			if s.mobile != nil {
				if !s.mobile.begin() {
					return nil, problem("NOT_RUNNING", "", "mobile session is stopping")
				}
				defer s.mobile.end()
				s.mobile.graph.Unlock()
				mobileGraphLocked = false
			}
			delay, err := nimboProbeGET(ctx, p, r.URL, expected)
			if err != nil {
				return nil, problem("DELAY_FAILED", "name", "outbound GET failed")
			}
			if s.ctx.Err() != nil {
				return nil, problem("NOT_RUNNING", "", "runtime stopped during check")
			}
			return map[string]any{"delayMs": delay}, nil
		}
		delay, err := p.URLTest(ctx, r.URL, expected)
		if err != nil {
			return nil, problem("DELAY_FAILED", "name", err.Error())
		}
		return map[string]any{"delayMs": delay}, nil
	default:
		return nil, problem("UNKNOWN_OPERATION", "operation", "operation not implemented")
	}
}

type oneShotProbeResult struct {
	proxy   C.Proxy
	delayMs uint16
	tested  bool
	healthy bool
}

// oneShotBestProxy probes a bounded snapshot in parallel and returns the
// lowest-latency healthy remote. It owns no ticker or detached goroutine; the
// caller's session context cancels every probe and waits for every worker.
func oneShotBestProxy(parent context.Context, candidates []C.Proxy, rawURL string, timeoutMs int, expected utils.IntRanges[uint16]) (C.Proxy, uint16, int, error) {
	results := make([]oneShotProbeResult, len(candidates))
	jobs := make(chan int, len(candidates))
	for i := range candidates {
		jobs <- i
	}
	close(jobs)
	workers := min(8, len(candidates))
	var wg sync.WaitGroup
	for worker := 0; worker < workers; worker++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for index := range jobs {
				if parent.Err() != nil {
					return
				}
				candidate := candidates[index]
				results[index].proxy = candidate
				results[index].tested = true
				ctx, cancel := context.WithTimeout(parent, time.Duration(timeoutMs)*time.Millisecond)
				delay, err := candidate.URLTest(ctx, rawURL, expected)
				cancel()
				results[index].delayMs = delay
				results[index].healthy = err == nil && candidate.AliveForTestUrl(rawURL)
			}
		}()
	}
	wg.Wait()
	var best C.Proxy
	var bestDelay uint16
	probed := 0
	for _, result := range results {
		if result.tested {
			probed++
		}
		if !result.healthy {
			continue
		}
		if best == nil || result.delayMs < bestDelay {
			best = result.proxy
			bestDelay = result.delayMs
		}
	}
	if best == nil {
		return nil, 0, probed, problem("NO_HEALTHY_PROXY", "group", "no remote node passed the requested HTTP health check; current selection was not changed")
	}
	return best, bestDelay, probed, nil
}

func nativeParse(d *inspection) (*config.Config, error) {
	oldIPv6 := resolver.DisableIPv6
	defer func() { resolver.DisableIPv6 = oldIPv6 }()
	oldLogLevel := log.Level()
	defer log.SetLevel(oldLogLevel)
	log.SetLevel(log.SILENT)
	source, err := effectiveYAML(d)
	if err != nil {
		return nil, err
	}
	raw, err := config.UnmarshalRawConfig(source)
	if err != nil {
		return nil, problem("INVALID_CONFIG", "$", err.Error())
	}
	// These defaults are explicit managed policy, never edits to the source.
	raw.IPv6 = false
	raw.FindProcessMode = process.FindProcessOff
	raw.LogLevel = log.SILENT
	// The managed resolver has no implicit geodata downloads. An explicit true
	// is rejected by admission; an omitted filter uses this documented default.
	raw.DNS.FallbackFilter.GeoIP = false
	if serialized, marshalErr := yaml.Marshal(raw); marshalErr == nil {
		d.finalConfig, _ = decodeDocument(string(serialized))
	}
	cfg, err := config.ParseRawConfig(raw)
	if err != nil {
		return nil, problem("INVALID_CONFIG", "$", err.Error())
	}
	return cfg, nil
}

func (m *manager) start(r request) (result any, err error) {
	m.mu.Lock()
	if expiry, ok := m.cancelledStarts[r.RequestID]; ok && time.Now().Before(expiry) {
		m.mu.Unlock()
		return nil, problem("START_CANCELLED", "requestId", "start request was cancelled before dispatch")
	}
	if m.state != "stopped" {
		m.mu.Unlock()
		return nil, problem("ALREADY_RUNNING", "", "single native instance; stop before replacing")
	}
	m.mu.Unlock()
	o := r.Options
	if runtime.GOOS == "android" && r.borrowedFD == nil {
		return nil, problem("PLATFORM_UNAVAILABLE", "operation", "Android start requires trusted StartAndroid entry")
	}
	android := o.NetworkOwner == "android-vpn" && r.borrowedFD != nil
	packet := r.packetOwner && o.NetworkOwner == "ios-packet-flow" && packetFlowCompiled
	if r.packetOwner && !packet {
		return nil, problem("INVALID_REQUEST", "options.networkOwner", "packet flow requires ios-packet-flow ownership")
	}
	if !android && !packet && o.NetworkOwner != "desktop-proxy" {
		return nil, problem("PLATFORM_UNAVAILABLE", "options.networkOwner", "exclusive mobile FD/TUN lifecycle not wired; never falls back to TCP")
	}
	if r.borrowedFD != nil && !android {
		return nil, problem("INVALID_REQUEST", "options.networkOwner", "StartAndroid requires android-vpn")
	}
	if android || packet {
		if android {
			if err = androidPlatformCheck(*r.borrowedFD); err != nil {
				return nil, err
			}
		}
		if o.MixedAddress != "" || o.ControllerAddress != "" || o.Secret != "" {
			return nil, problem("INVALID_REQUEST", "options", "mobile has no mixed/controller listener")
		}
		protectorLock.RLock()
		protected := socketProtector != nil
		protectorLock.RUnlock()
		if !protected {
			return nil, problem("PROTECTION_REQUIRED", "", "install SocketProtector while stopped")
		}
	}
	if !filepath.IsAbs(o.DataDir) {
		return nil, problem("INVALID_REQUEST", "options.dataDir", "absolute app-owned directory required")
	}
	if o.StartupTimeoutMs == 0 {
		o.StartupTimeoutMs = 20000
	}
	if o.StartupTimeoutMs < 1 || o.StartupTimeoutMs > 25000 {
		return nil, problem("INVALID_REQUEST", "options.startupTimeoutMs", "expected 1..25000")
	}
	if !android && !packet {
		if err = loopbackAddress(o.MixedAddress); err != nil {
			return nil, err
		}
	}
	if o.ControllerAddress != "" {
		if err = loopbackAddress(o.ControllerAddress); err != nil {
			return nil, err
		}
		if len(o.Secret) < 32 {
			return nil, problem("INVALID_REQUEST", "options.secret", "random bearer token of at least 32 bytes required")
		}
	}
	d, err := inspect(r.YAML)
	if err != nil {
		return nil, err
	}
	if android || packet {
		if packet {
			if err = packetRuntimePolicy(d); err != nil {
				return nil, err
			}
		}
		if err = androidRuntimePolicy(d); err != nil {
			return nil, err
		}
		d.android = true // shared source-preserving mobile ownership policy
	} else if len(d.StrictIssues) > 0 {
		return nil, &d.StrictIssues[0]
	}
	ctx, cancel := context.WithCancel(context.Background())
	startupTimer := time.AfterFunc(time.Duration(o.StartupTimeoutMs)*time.Millisecond, cancel)
	defer startupTimer.Stop()
	s := &session{doc: d, home: filepath.Clean(o.DataDir), oldHome: C.Path.HomeDir(), oldIPv6: resolver.DisableIPv6, oldMode: tunnel.Mode(), oldProcess: tunnel.FindProcessMode(), oldStoreSelected: profile.StoreSelected.Load(), oldLogLevel: log.Level(), oldDNS: saveDNS(), ctx: ctx, cancel: cancel}
	if android {
		s.mobile = newMobileSession(ctx, cancel)
		s.oldAndroidGlobals = captureAndroidGlobals()
	} else if packet {
		s.oldAndroidGlobals = captureAndroidGlobals()
	}
	m.mu.Lock()
	if expiry, ok := m.cancelledStarts[r.RequestID]; ok && time.Now().Before(expiry) {
		m.mu.Unlock()
		cancel()
		if s.mobile != nil {
			s.mobile.close()
		}
		return nil, problem("START_CANCELLED", "requestId", "start request cancelled during validation")
	}
	m.state = "starting"
	m.cancel = cancel
	m.session = s
	m.startRequestID = r.RequestID
	m.info = runtimeStatus{NetworkOwner: o.NetworkOwner, SourceSHA256: d.SourceSHA256}
	m.mu.Unlock()
	committed := false
	defer func() {
		if !committed {
			s.cleanup()
			m.mu.Lock()
			m.session = nil
			m.cancel = nil
			m.state = "stopped"
			m.info = runtimeStatus{}
			m.startRequestID = ""
			m.mu.Unlock()
		}
	}()
	if err = os.MkdirAll(s.home, 0700); err != nil {
		return nil, err
	}
	C.SetHomeDir(s.home)
	if err = openSessionCache(); err != nil {
		return nil, problem("CACHE_OPEN", "options.dataDir", err.Error())
	}
	s.cacheOpened = true
	if packet {
		s.cfg, err = nativeParsePacket(d, o.PacketIPv6, o.PacketSystemDNS)
		m.lastDiagnosticConfig = d.finalConfig
	} else if android {
		s.cfg, err = nativeParseAndroid(d, o.AndroidIPv6, o.AndroidSystemDNS)
		m.lastDiagnosticConfig = d.finalConfig
	} else {
		s.cfg, err = nativeParse(d)
	}
	if err != nil {
		return nil, err
	}
	log.SetLevel(s.cfg.General.LogLevel)
	if err = rebindProviders(s.cfg, d, s.home); err != nil {
		return nil, err
	}
	if err = rebindRuleProviders(s.cfg, d, s.home); err != nil {
		return nil, err
	}

	// Close native fetcher contexts immediately when start is canceled, rather than
	// queuing cancellation behind a provider HTTP request on the operation lock.
	s.watchDone = make(chan struct{})
	go func() { defer close(s.watchDone); <-ctx.Done(); s.closeProviders() }()
	if s.mobile != nil {
		s.mobile.cfg = s.cfg
		// The native Mihomo DNS resolver/enhancer is installed below. The legacy
		// mobile resolver remains only as a lifecycle lock for bridge mutations.
	} else if !packet {
		applyInternalDNS(s.cfg)
	}
	for _, p := range s.cfg.Providers {
		if err = ctx.Err(); err != nil {
			return nil, problem("START_CANCELLED", "", err.Error())
		}
		if err = p.Initial(); err != nil {
			return nil, problem("PROVIDER_INITIALIZATION", "", err.Error())
		}
	}
	for _, p := range s.cfg.RuleProviders {
		if err = ctx.Err(); err != nil {
			return nil, problem("START_CANCELLED", "", err.Error())
		}
		if err = p.Initial(); err != nil {
			return nil, problem("RULE_PROVIDER_INITIALIZATION", "", err.Error())
		}
	}
	if err = ctx.Err(); err != nil {
		return nil, problem("START_CANCELLED", "", err.Error())
	}
	profile.StoreSelected.Store(s.cfg.Profile.StoreSelected)
	if s.cfg.Profile.StoreSelected {
		for name, selected := range cachefile.Cache().SelectedMap() {
			if p := s.cfg.Proxies[name]; p != nil {
				if selector, ok := p.Adapter().(outboundgroup.SelectAble); ok {
					_ = selector.Set(selected)
				}
			}
		}
	}
	tunnel.OnSuspend()
	tunnel.UpdateProxies(s.cfg.Proxies, s.cfg.Providers)
	tunnel.UpdateRules(s.cfg.Rules, s.cfg.SubRules, s.cfg.RuleProviders)
	tunnel.SetMode(s.cfg.General.Mode)
	if android {
		tunnel.SetFindProcessMode(s.cfg.General.FindProcessMode)
	} else {
		tunnel.SetFindProcessMode(process.FindProcessOff)
	}
	resolver.DisableIPv6 = !s.cfg.General.IPv6
	if android || packet {
		if err = applyUpstreamAndroidComponents(s.cfg); err != nil {
			return nil, problem("MIHOMO_INITIALIZATION_FAILED", "", err.Error())
		}
	}
	if packet {
		s.packet, err = startPacketRuntime(s)
		if err != nil {
			return nil, problem("TUN_START_FAILED", "packetFlow", err.Error())
		}
		s.tun = s.packet
	} else if s.mobile != nil {
		s.tun, err = startAndroidTun(*r.borrowedFD, s.cfg)
		if err != nil {
			return nil, problem("TUN_START_FAILED", "borrowedFD", err.Error())
		}
	} else if err = s.listenMixed(o.MixedAddress); err != nil {
		return nil, problem("LISTEN_FAILED", "options.mixedAddress", err.Error())
	}
	if o.ControllerAddress != "" {
		s.controller, err = newController(o.ControllerAddress, o.Secret)
		if err != nil {
			return nil, problem("LISTEN_FAILED", "options.controllerAddress", err.Error())
		}
	}
	m.mu.Lock()
	if !startupTimer.Stop() || ctx.Err() != nil {
		m.mu.Unlock()
		return nil, problem("START_CANCELLED", "", "start deadline or cancellation")
	}
	tunnel.OnRunning()
	m.generation++
	m.state = "running"
	if s.accepted != nil {
		m.info.MixedAddress = s.accepted.Addr().String()
	}
	m.info.TunReady = s.tun != nil
	if s.controller != nil {
		m.info.ControllerAddress = s.controller.address
	}
	committed = true
	result = m.statusLocked()
	m.mu.Unlock()
	return result, nil
}

func (s *session) cleanup() {
	if s.cancel != nil {
		s.cancel()
	}
	s.closeProviders()
	if s.watchDone != nil {
		<-s.watchDone
	}
	// Health check workers are canceled and joined by provider.Close above;
	// group failure workers are joined before borrowed TUN ownership is released.
	if s.cfg != nil {
		for _, p := range s.cfg.Proxies {
			_ = p.Close()
		}
	}
	if s.tun != nil {
		_ = s.tun.Close()
		s.tun = nil
	}
	if s.mobile != nil {
		s.mobile.close()
	}
	tunnel.OnSuspend()
	if s.controller != nil {
		s.controller.close()
	}
	if s.accepted != nil {
		_ = s.accepted.Close()
	}
	if s.mixed != nil {
		_ = s.mixed.Close()
	}
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool { _ = c.Close(); return true })
	if s.cacheOpened {
		storeSessionFakeIP(s.cfg)
		closeSessionCache()
		s.cacheOpened = false
	}
	tunnel.UpdateProxies(nil, nil)
	tunnel.UpdateRules(nil, nil, nil)
	tunnel.UpdateSniffer(nil)
	tunnel.SetMode(s.oldMode)
	tunnel.SetFindProcessMode(s.oldProcess)
	profile.StoreSelected.Store(s.oldStoreSelected)
	log.SetLevel(s.oldLogLevel)
	resolver.DisableIPv6 = s.oldIPv6
	s.oldDNS.restore()
	if s.doc != nil && s.doc.android {
		s.oldAndroidGlobals.restore()
	}
	C.SetHomeDir(s.oldHome)
}

// Store the allocation cursor before closing the per-source cache. Otherwise
// Mihomo sees persisted entries without a cursor and flushes them on restart.
func storeSessionFakeIP(cfg *config.Config) {
	if cfg == nil || cfg.DNS == nil || cfg.Profile == nil || !cfg.Profile.StoreFakeIP {
		return
	}
	if cfg.DNS.FakeIPPool != nil {
		cfg.DNS.FakeIPPool.StoreState()
	}
	if cfg.DNS.FakeIPPool6 != nil {
		cfg.DNS.FakeIPPool6.StoreState()
	}
}
func (s *session) closeProviders() {
	s.closeProvidersOnce.Do(func() {
		if s.cfg != nil {
			for _, p := range s.cfg.Providers {
				closeProvider(p)
			}
			for _, p := range s.cfg.RuleProviders {
				closeProvider(p)
			}
		}
	})
}
func (m *manager) stop() (any, error) {
	m.mu.Lock()
	s := m.session
	m.state = "stopping"
	m.mu.Unlock()
	if s != nil {
		s.cleanup()
	}
	m.mu.Lock()
	m.session = nil
	m.cancel = nil
	m.state = "stopped"
	m.info = runtimeStatus{}
	m.startRequestID = ""
	result := m.statusLocked()
	m.mu.Unlock()
	return result, nil
}
func snapshot(s *session) (map[string]any, error) {
	groups := map[string]any{}
	proxies := map[string]any{}
	providers := map[string]any{}
	for name, p := range s.cfg.Proxies {
		b, err := json.Marshal(p)
		if err != nil {
			return nil, err
		}
		proxies[name] = json.RawMessage(b)
		if _, ok := p.Adapter().(outboundgroup.ProxyGroup); ok {
			b, err := json.Marshal(p)
			if err != nil {
				return nil, err
			}
			groups[name] = json.RawMessage(b)
		}
	}
	for name, p := range s.cfg.Providers {
		ps := p.Proxies()
		if ps == nil {
			ps = []C.Proxy{}
		}
		b, err := json.Marshal(ps)
		if err != nil {
			return nil, err
		}
		providers[name] = map[string]any{"name": name, "vehicleType": p.VehicleType().String(), "version": p.Version(), "proxies": json.RawMessage(b)}
	}
	ruleProviders := map[string]any{}
	for name, p := range s.cfg.RuleProviders {
		ruleProviders[name] = map[string]any{"name": name, "vehicleType": p.VehicleType().String(), "behavior": p.Behavior().String(), "ruleCount": p.Count()}
	}
	return map[string]any{"groups": groups, "proxies": proxies, "providers": providers, "ruleProviders": ruleProviders}, nil
}

// applyUpstreamAndroidComponents installs the upstream rule and DNS services
// consumed by Mihomo's own sing-tun listener. It deliberately does not call
// executor.ApplyConfig: that entry point can create host listeners, mutate
// routes/firewall, start system NTP, download UI assets, and own a second TUN.
func applyUpstreamAndroidComponents(cfg *config.Config) error {
	if cfg == nil || cfg.General == nil || cfg.DNS == nil {
		return errors.New("incomplete parsed Mihomo configuration")
	}
	resolver.DefaultHosts = resolver.NewHosts(cfg.Hosts)
	resolver.UseSystemHosts = cfg.DNS.UseSystemHosts
	c := cfg.DNS
	if !c.Enable {
		resolver.DefaultResolver = nil
		resolver.DefaultHostMapper = nil
		resolver.DefaultService = nil
		resolver.ProxyServerHostResolver = nil
		resolver.DirectHostResolver = nil
	} else {
		ipv6 := c.IPv6 && cfg.General.IPv6
		r := mihomoDNS.NewResolver(mihomoDNS.Config{
			Main: c.NameServer, Fallback: c.Fallback, IPv6: ipv6, IPv6Timeout: c.IPv6Timeout,
			FallbackIPFilter: c.FallbackIPFilter, FallbackDomainFilter: c.FallbackDomainFilter,
			FallbackLazyQuery: c.FallbackLazyQuery, Default: c.DefaultNameserver,
			Policy: c.NameServerPolicy, ProxyServer: c.ProxyServerNameserver,
			ProxyServerPolicy: c.ProxyServerPolicy, DirectServer: c.DirectNameServer,
			DirectFollowPolicy: c.DirectFollowPolicy, CacheAlgorithm: c.CacheAlgorithm,
			CacheMaxSize: c.CacheMaxSize,
		})
		mapper := mihomoDNS.NewEnhancer(mihomoDNS.EnhancerConfig{
			IPv6: ipv6, EnhancedMode: c.EnhancedMode, FakeIPPool: c.FakeIPPool,
			FakeIPPool6: c.FakeIPPool6, FakeIPSkipper: c.FakeIPSkipper,
			FakeIPTTL: c.FakeIPTTL, UseHosts: c.UseHosts,
		})
		resolver.DefaultResolver = r
		resolver.DefaultHostMapper = mapper
		resolver.DefaultService = mihomoDNS.NewService(r, mapper)
		if r.ProxyResolver.Invalid() {
			resolver.ProxyServerHostResolver = r.ProxyResolver
		} else {
			resolver.ProxyServerHostResolver = r.Resolver
		}
		if r.DirectResolver.Invalid() {
			resolver.DirectHostResolver = r.DirectResolver
		} else {
			resolver.DirectHostResolver = r.Resolver
		}
	}
	dispatcher, err := sniffer.NewDispatcher(cfg.Sniffer)
	if err != nil {
		return fmt.Errorf("initialize Mihomo sniffer: %w", err)
	}
	tunnel.UpdateSniffer(dispatcher)
	tunnel.SetSniffing(cfg.General.Sniffing)
	dialer.SetTcpConcurrent(cfg.General.TCPConcurrent)
	inbound.SetTfo(cfg.General.InboundTfo)
	inbound.SetMPTCP(cfg.General.InboundMPTCP)
	keepalive.SetKeepAliveIdle(time.Duration(cfg.General.KeepAliveIdle) * time.Second)
	keepalive.SetKeepAliveInterval(time.Duration(cfg.General.KeepAliveInterval) * time.Second)
	keepalive.SetDisableKeepAlive(cfg.General.DisableKeepAlive)
	adapter.UnifiedDelay.Store(cfg.General.UnifiedDelay)
	geodata.SetGeodataMode(cfg.General.GeodataMode)
	geodata.SetLoader(cfg.General.GeodataLoader)
	geodata.SetSiteMatcher(cfg.General.GeositeMatcher)
	geodata.SetGeoIpUrl(cfg.General.GeoXUrl.GeoIp)
	geodata.SetMmdbUrl(cfg.General.GeoXUrl.Mmdb)
	geodata.SetGeoSiteUrl(cfg.General.GeoXUrl.GeoSite)
	geodata.SetASNUrl(cfg.General.GeoXUrl.ASN)
	mihomoHTTP.SetUA(cfg.General.GlobalUA)
	resource.SetETag(cfg.General.ETagSupport)
	return nil
}

// SocketProtector is gomobile-compatible. The platform must protect/bind every
// outgoing socket before connect. No FD ownership is inferred from an integer.
type SocketProtector interface{ Protect(fd int64) bool }

// StartIOS preserves the deprecated FD ABI gate. New callers must use
// StartIOSPacketFlow: public NEPacketTunnelFlow, no borrowed utun FD scan.
func StartIOS(requestJSON string, borrowedFD int64) string {
	code := "PLATFORM_UNAVAILABLE"
	message := "exclusive iOS TUN FD bridge is not implemented"
	if borrowedFD <= 0 {
		code = "INVALID_FD"
		message = "borrowed TUN descriptor must be positive; no utun creation permitted"
	}
	var r request
	_ = json.Unmarshal([]byte(requestJSON), &r)
	singleton.mu.Lock()
	gen := singleton.generation
	singleton.mu.Unlock()
	b, _ := json.Marshal(response{APIVersion: 1, RequestID: r.RequestID, Generation: gen, Error: problem(code, "borrowedFD", message)})
	return string(b)
}
