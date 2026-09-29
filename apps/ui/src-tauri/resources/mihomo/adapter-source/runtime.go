// Package mihomocore embeds the pinned Mihomo core behind a versioned JSON facade.
// It never changes the host system proxy, routes, DNS configuration or firewall.
package mihomocore

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	_ "github.com/metacubex/mihomo/hub/executor" // config temporaryUpdateGeneral link target; never ApplyConfig
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

const coreVersion = "v1.19.31"
const coreCommit = "ab405bad5beeeac8b003bb01f60f134f6df54471"

type startOptions struct {
	DataDir           string `json:"dataDir"`
	NetworkOwner      string `json:"networkOwner"`
	MixedAddress      string `json:"mixedAddress"`
	ControllerAddress string `json:"controllerAddress"`
	Secret            string `json:"secret"`
	StartupTimeoutMs  int    `json:"startupTimeoutMs,omitempty"`
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
	oldDNS             dnsState
	ctx                context.Context
	cancel             context.CancelFunc
	watchDone          chan struct{}
	closeProvidersOnce sync.Once
	cacheOpened        bool
}
type manager struct {
	mu              sync.Mutex
	op              sync.Mutex
	generation      uint64
	state           string
	cancel          context.CancelFunc
	session         *session
	info            runtimeStatus
	startRequestID  string
	cancelledStarts map[string]time.Time
}

var singleton = manager{state: "stopped"}

// Invoke is deliberately string-only for gomobile and C ABI wrappers. Native
// operations are serialized; status and stop cancellation bypass the op lock.
func Invoke(input string) (output string) {
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
	m.mu.Unlock()
	if r.Operation == "inspect" {
		return inspect(r.YAML)
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
	switch r.Operation {
	case "snapshot":
		return snapshot(s)
	case "select":
		p, ok := s.cfg.Proxies[r.Group]
		if !ok {
			return nil, problem("NOT_FOUND", "group", "group not found")
		}
		selector, ok := p.Adapter().(outboundgroup.SelectAble)
		if !ok {
			return nil, problem("NOT_SELECTABLE", "group", "native group does not support selection")
		}
		if err := selector.Set(r.Name); err != nil {
			return nil, problem("INVALID_SELECTION", "name", err.Error())
		}
		return snapshot(s)
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
	case "delay":
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
		delay, err := p.URLTest(ctx, r.URL, expected)
		if err != nil {
			return nil, problem("DELAY_FAILED", "name", err.Error())
		}
		return map[string]any{"delayMs": delay}, nil
	default:
		return nil, problem("UNKNOWN_OPERATION", "operation", "operation not implemented")
	}
}

func nativeParse(d *inspection) (*config.Config, error) {
	oldIPv6 := resolver.DisableIPv6
	defer func() { resolver.DisableIPv6 = oldIPv6 }()
	raw, err := config.UnmarshalRawConfig([]byte(d.OriginalYAML))
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
	log.SetLevel(log.SILENT)
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
	if o.NetworkOwner != "desktop-proxy" {
		return nil, problem("PLATFORM_UNAVAILABLE", "options.networkOwner", "exclusive mobile FD/TUN lifecycle not wired; never falls back to TCP")
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
	if err = loopbackAddress(o.MixedAddress); err != nil {
		return nil, err
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
	if len(d.StrictIssues) > 0 {
		return nil, &d.StrictIssues[0]
	}
	ctx, cancel := context.WithCancel(context.Background())
	startupTimer := time.AfterFunc(time.Duration(o.StartupTimeoutMs)*time.Millisecond, cancel)
	defer startupTimer.Stop()
	s := &session{doc: d, home: filepath.Clean(o.DataDir), oldHome: C.Path.HomeDir(), oldIPv6: resolver.DisableIPv6, oldMode: tunnel.Mode(), oldProcess: tunnel.FindProcessMode(), oldDNS: saveDNS(), ctx: ctx, cancel: cancel}
	m.mu.Lock()
	if expiry, ok := m.cancelledStarts[r.RequestID]; ok && time.Now().Before(expiry) {
		m.mu.Unlock()
		cancel()
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
	s.cfg, err = nativeParse(d)
	if err != nil {
		return nil, err
	}
	if err = rebindProviders(s.cfg, d, s.home); err != nil {
		return nil, err
	}
	if err = rebindRuleProviders(s.cfg, d, s.home); err != nil {
		return nil, err
	}
	if err = openSessionCache(); err != nil {
		return nil, problem("CACHE_OPEN", "options.dataDir", err.Error())
	}
	s.cacheOpened = true
	// Close native fetcher contexts immediately when start is canceled, rather than
	// queuing cancellation behind a provider HTTP request on the operation lock.
	s.watchDone = make(chan struct{})
	go func() { defer close(s.watchDone); <-ctx.Done(); s.closeProviders() }()
	applyInternalDNS(s.cfg)
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
	tunnel.OnSuspend()
	tunnel.UpdateProxies(s.cfg.Proxies, s.cfg.Providers)
	tunnel.UpdateRules(s.cfg.Rules, s.cfg.SubRules, s.cfg.RuleProviders)
	tunnel.SetMode(s.cfg.General.Mode)
	tunnel.SetFindProcessMode(process.FindProcessOff)
	resolver.DisableIPv6 = true
	if err = s.listenMixed(o.MixedAddress); err != nil {
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
	m.info.MixedAddress = s.accepted.Addr().String()
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
	if s.watchDone != nil {
		<-s.watchDone
	}
	s.closeProviders()
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
	if s.cfg != nil {
		for _, p := range s.cfg.Proxies {
			_ = p.Close()
		}
	}
	if s.cacheOpened {
		closeSessionCache()
		s.cacheOpened = false
	}
	tunnel.UpdateProxies(nil, nil)
	tunnel.UpdateRules(nil, nil, nil)
	tunnel.SetMode(s.oldMode)
	tunnel.SetFindProcessMode(s.oldProcess)
	resolver.DisableIPv6 = s.oldIPv6
	s.oldDNS.restore()
	C.SetHomeDir(s.oldHome)
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
	providers := map[string]any{}
	for name, p := range s.cfg.Proxies {
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
	return map[string]any{"groups": groups, "providers": providers, "ruleProviders": ruleProviders}, nil
}

// SocketProtector is gomobile-compatible. The platform must protect/bind every
// outgoing socket before connect. No FD ownership is inferred from an integer.
type SocketProtector interface{ Protect(fd int64) bool }

// StartIOS is an explicit gate, not a fake successful TUN attach. A future bridge
// must dup(borrowedFD), set nonblocking, pass ONLY the duplicate to sing-tun,
// disable host auto-route/redirect/interface discovery, and acknowledge readiness
// after native listener success. It must share the existing LibXray Go runtime.
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
