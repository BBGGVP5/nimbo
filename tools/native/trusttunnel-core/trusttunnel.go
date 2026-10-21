// Package trusttunnelcore exposes a TrustTunnel (AdGuard) endpoint as an
// authenticated loopback SOCKS5 proxy for the in-process Xray core on Android
// and iOS. The protocol client is Mihomo's transport/trusttunnel (the same
// v1.19.32 already linked into the mobile Go archive), so no second runtime or
// native library is added. Desktop uses the official CLI client instead.
//
// Limits of this client compared with the official one, surfaced to users:
// no anti-DPI ClientHello splitting, no client_random prefix/auth key and a
// single endpoint address per connection.
package trusttunnelcore

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"net"
	"net/netip"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/transport/trusttunnel"
	"github.com/metacubex/mihomo/transport/vmess"
)

// Version identifies the protocol implementation, not the TrustTunnel spec.
const Version = "mihomo-v1.19.32"

const (
	defaultTimeout = 10 * time.Second
	maxRequest     = 64 * 1024
)

// Request is produced by the host app from an already parsed tt:// link.
type Request struct {
	// Link is an alternative to the fields below: a raw tt:// deep link that
	// is decoded and resolved here (iOS extension has no shared Kotlin parser).
	Link string `json:"link"`
	// Server is the dial address `ip:port`. Hosts resolve it before the VPN
	// interface exists so the lookup can never loop through the tunnel.
	Server string `json:"server"`
	// SNI sent in the ClientHello (custom_sni or hostname).
	SNI string `json:"sni"`
	// VerifyName is the name the endpoint certificate is issued for; empty
	// means SNI.
	VerifyName        string `json:"verifyName"`
	Username          string `json:"username"`
	Password          string `json:"password"`
	HTTP3             bool   `json:"http3"`
	SkipVerification  bool   `json:"skipVerification"`
	Certificate       string `json:"certificate"`
	ClientFingerprint string `json:"clientFingerprint"`
	// Listen is the loopback SOCKS5 address, usually 127.0.0.1:0.
	Listen        string `json:"listen"`
	SocksUsername string `json:"socksUsername"`
	SocksPassword string `json:"socksPassword"`
	TimeoutMs     int    `json:"timeoutMs"`
}

// Protector marks an outbound socket so it bypasses the device VPN (Android
// VpnService.protect). Nil means no protection is needed (iOS extension).
type Protector func(fd uintptr) bool

// Error is a stable, secret-free code for the host UI.
type Error struct {
	Code   string
	Detail string
}

func (e *Error) Error() string {
	if e.Detail == "" {
		return e.Code
	}
	return e.Code + ": " + e.Detail
}

func fail(code, detail string) error { return &Error{Code: code, Detail: detail} }

func (r *Request) timeout() time.Duration {
	if r.TimeoutMs <= 0 {
		return defaultTimeout
	}
	if r.TimeoutMs > 30000 {
		return 30 * time.Second
	}
	return time.Duration(r.TimeoutMs) * time.Millisecond
}

func (r *Request) validate() error {
	if _, err := netip.ParseAddrPort(r.Server); err != nil {
		return fail("REQUEST", "server must be a resolved ip:port")
	}
	if strings.TrimSpace(r.SNI) == "" || r.Username == "" || r.Password == "" {
		return fail("REQUEST", "sni, username and password are required")
	}
	return nil
}

// certificatePin returns the SHA-256 of the chain's last certificate (the
// root, or the self-signed leaf). Mihomo then verifies the whole presented
// chain against it, which matches the official client's custom-CA mode.
func certificatePin(chain string) (string, error) {
	var last []byte
	rest := []byte(chain)
	for {
		var block *pem.Block
		block, rest = pem.Decode(rest)
		if block == nil {
			break
		}
		if block.Type == "CERTIFICATE" {
			last = block.Bytes
		}
	}
	if last == nil {
		return "", fail("CONFIG", "certificate is not a PEM chain")
	}
	sum := sha256.Sum256(last)
	return hex.EncodeToString(sum[:]), nil
}

// netDialer creates protected sockets directly. It never goes through Mihomo's
// global DefaultSocketHook, which only admits sockets of a running Mihomo
// session and would reject this independent client.
type netDialer struct{ protect Protector }

func (d netDialer) control(_, _ string, raw syscall.RawConn) error {
	if d.protect == nil {
		return nil
	}
	protected := false
	if err := raw.Control(func(fd uintptr) { protected = d.protect(fd) }); err != nil {
		return err
	}
	if !protected {
		return errors.New("socket protection failed")
	}
	return nil
}

func (d netDialer) DialContext(ctx context.Context, network, address string) (net.Conn, error) {
	nd := &net.Dialer{Control: d.control, KeepAlive: 30 * time.Second}
	return nd.DialContext(ctx, network, address)
}

func (d netDialer) ListenPacket(ctx context.Context, network, address string, _ netip.AddrPort) (net.PacketConn, error) {
	if address == "" {
		address = ":0"
	}
	lc := &net.ListenConfig{Control: d.control}
	return lc.ListenPacket(ctx, network, address)
}

// hostResolver is the platform resolver. Go's pure resolver would need
// /etc/resolv.conf, which neither the iOS extension nor Android provides; the
// system resolver is safe because Nimbo's own process never routes through its
// tunnel (iOS extension traffic; Android app excluded from its VpnService).
func hostResolver() *net.Resolver { return net.DefaultResolver }

func newClient(ctx context.Context, r *Request, protect Protector) (*trusttunnel.Client, error) {
	if r.Link != "" {
		resolved, err := requestFromLink(ctx, *r, hostResolver())
		if err != nil {
			return nil, err
		}
		*r = resolved
	}
	if err := r.validate(); err != nil {
		return nil, err
	}
	fingerprint := r.ClientFingerprint
	if fingerprint == "" {
		fingerprint = "chrome"
	}
	tlsConfig := &vmess.TLSConfig{
		Host:              strings.TrimSpace(r.SNI),
		SkipCertVerify:    r.SkipVerification,
		ClientFingerprint: fingerprint,
	}
	if name := strings.TrimSpace(r.VerifyName); name != "" && name != tlsConfig.Host {
		tlsConfig.NameCertVerify = name
	}
	if !r.SkipVerification && strings.TrimSpace(r.Certificate) != "" {
		pin, err := certificatePin(r.Certificate)
		if err != nil {
			return nil, err
		}
		tlsConfig.FingerPrint = pin
	}
	client, err := trusttunnel.NewClient(ctx, trusttunnel.ClientOptions{
		Dialer:      netDialer{protect: protect},
		DialOptions: func() []dialer.Option { return nil },
		Server:      r.Server,
		Username:    r.Username,
		Password:    r.Password,
		TLSConfig:   tlsConfig,
		QUIC:        r.HTTP3,
	})
	if err != nil {
		return nil, fail("CONFIG", err.Error())
	}
	return client, nil
}

// classify maps transport failures to codes without leaking credentials.
func classify(err error) error {
	text := err.Error()
	switch {
	case strings.Contains(text, "status code: 407"), strings.Contains(text, "status code: 401"),
		strings.Contains(text, "status code: 403"):
		return fail("AUTH_REJECTED", "endpoint rejected the credentials")
	case strings.Contains(text, "certificate"), strings.Contains(text, "x509"),
		strings.Contains(text, "fingerprint"):
		return fail("TLS_VERIFY", "endpoint certificate was not accepted")
	case errors.Is(err, context.DeadlineExceeded), strings.Contains(text, "timeout"):
		return fail("TIMEOUT", "endpoint did not answer in time")
	default:
		return fail("UNREACHABLE", "endpoint is unreachable")
	}
}

// Probe performs one authenticated health check through a throwaway client
// and returns its latency. It is the honest "ping" for TrustTunnel: a TCP
// connect alone proves nothing about credentials or HTTP/3 reachability.
func Probe(r Request, protect Protector) (time.Duration, error) {
	ctx, cancel := context.WithTimeout(context.Background(), r.timeout())
	defer cancel()
	client, err := newClient(ctx, &r, protect)
	if err != nil {
		return 0, err
	}
	defer client.Close()
	started := time.Now()
	if err := client.HealthCheck(ctx); err != nil {
		return 0, classify(err)
	}
	return time.Since(started), nil
}

// Runtime owns one TrustTunnel client and its loopback SOCKS5 listener.
type Runtime struct {
	ctx      context.Context
	cancel   context.CancelFunc
	client   *trusttunnel.Client
	socks    *socksServer
	latency  time.Duration
	closed   sync.Once
	stopped  chan struct{}
	resolver *net.Resolver
}

// Start verifies the endpoint with a health check before exposing SOCKS, so a
// wrong password or unreachable server fails the connection attempt instead of
// producing a silently dead tunnel.
func Start(r Request, protect Protector) (*Runtime, error) {
	ctx, cancel := context.WithCancel(context.Background())
	client, err := newClient(ctx, &r, protect)
	if err != nil {
		cancel()
		return nil, err
	}
	checkCtx, checkCancel := context.WithTimeout(ctx, r.timeout())
	started := time.Now()
	err = client.HealthCheck(checkCtx)
	checkCancel()
	if err != nil {
		_ = client.Close()
		cancel()
		return nil, classify(err)
	}
	listen := r.Listen
	if listen == "" {
		listen = "127.0.0.1:0"
	}
	if addr, perr := netip.ParseAddrPort(listen); perr != nil || !addr.Addr().IsLoopback() {
		_ = client.Close()
		cancel()
		return nil, fail("REQUEST", "listen must be a loopback ip:port")
	}
	rt := &Runtime{
		ctx:      ctx,
		cancel:   cancel,
		client:   client,
		latency:  time.Since(started),
		stopped:  make(chan struct{}),
		resolver: hostResolver(),
	}
	socks, err := newSocksServer(ctx, listen, r.SocksUsername, r.SocksPassword, rt)
	if err != nil {
		_ = client.Close()
		cancel()
		return nil, fail("LISTEN", "cannot open loopback SOCKS listener")
	}
	rt.socks = socks
	go func() {
		socks.serve()
		close(rt.stopped)
	}()
	return rt, nil
}

func (r *Runtime) Port() int { return r.socks.port() }

func (r *Runtime) Latency() time.Duration { return r.latency }

func (r *Runtime) Running() bool {
	select {
	case <-r.stopped:
		return false
	default:
		return r.ctx.Err() == nil
	}
}

// NetworkChanged drops pooled HTTP/2 or QUIC sessions bound to the old path.
func (r *Runtime) NetworkChanged() { r.client.ResetConnections() }

func (r *Runtime) Close() {
	r.closed.Do(func() {
		r.cancel()
		r.socks.close()
		_ = r.client.Close()
	})
}

// --- JSON surface shared by the gomobile (Android) and C (iOS) bridges. ---

var singleton struct {
	sync.Mutex
	runtime *Runtime
}

func respond(v map[string]any) string {
	b, err := json.Marshal(v)
	if err != nil {
		return `{"ok":false,"error":"RESPONSE"}`
	}
	return string(b)
}

func errorResponse(err error) string {
	var typed *Error
	if errors.As(err, &typed) {
		return respond(map[string]any{"ok": false, "error": typed.Code, "detail": typed.Detail})
	}
	return respond(map[string]any{"ok": false, "error": "INTERNAL"})
}

func decode(request string) (Request, error) {
	var r Request
	if len(request) > maxRequest || json.Unmarshal([]byte(request), &r) != nil {
		return r, fail("REQUEST", "malformed request")
	}
	return r, nil
}

// StartJSON replaces any running instance. Response: {"ok":true,"port":N,
// "latencyMs":N,"version":"..."} or {"ok":false,"error":CODE,"detail":"..."}.
func StartJSON(request string, protect Protector) string {
	r, err := decode(request)
	if err != nil {
		return errorResponse(err)
	}
	singleton.Lock()
	defer singleton.Unlock()
	if singleton.runtime != nil {
		singleton.runtime.Close()
		singleton.runtime = nil
	}
	rt, err := Start(r, protect)
	if err != nil {
		return errorResponse(err)
	}
	singleton.runtime = rt
	return respond(map[string]any{
		"ok": true, "port": rt.Port(), "latencyMs": rt.Latency().Milliseconds(), "version": Version,
	})
}

func StopJSON() {
	singleton.Lock()
	defer singleton.Unlock()
	if singleton.runtime != nil {
		singleton.runtime.Close()
		singleton.runtime = nil
	}
}

func StatusJSON() string {
	singleton.Lock()
	defer singleton.Unlock()
	running := singleton.runtime != nil && singleton.runtime.Running()
	return respond(map[string]any{"ok": true, "running": running, "version": Version})
}

func NetworkChanged() {
	singleton.Lock()
	defer singleton.Unlock()
	if singleton.runtime != nil {
		singleton.runtime.NetworkChanged()
	}
}

// ProbeJSON response: {"ok":true,"latencyMs":N} or an error object.
func ProbeJSON(request string, protect Protector) string {
	r, err := decode(request)
	if err != nil {
		return errorResponse(err)
	}
	latency, err := Probe(r, protect)
	if err != nil {
		return errorResponse(err)
	}
	ms := latency.Milliseconds()
	if ms < 1 {
		ms = 1
	}
	return respond(map[string]any{"ok": true, "latencyMs": ms})
}
