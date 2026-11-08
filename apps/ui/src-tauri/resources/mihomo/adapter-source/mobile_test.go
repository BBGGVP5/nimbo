package mihomocore

import (
	"bufio"
	"context"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/dialer"
	C "github.com/metacubex/mihomo/constant"
	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/rules"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/sing/common/buf"
	M "github.com/metacubex/sing/common/metadata"
	N "github.com/metacubex/sing/common/network"
	D "github.com/miekg/dns"
)

const mobileConfig = `mode: global
dns:
  enable: true
  nameserver: [127.0.0.1:5353]
proxies: [{name: local, type: direct}]
proxy-groups: [{name: GLOBAL, type: select, proxies: [local], empty-fallback: REJECT}]
`

const mobileRuleConfig = `mode: rule
dns:
  enable: true
  nameserver: [127.0.0.1:5353]
proxies:
  - {name: port-443, type: direct}
  - {name: fallback, type: direct}
rules:
  - DST-PORT,443,port-443
  - MATCH,fallback
`

type mobileProtector struct {
	accept   atomic.Bool
	calls    atomic.Int64
	panicNow bool
}

func (p *mobileProtector) Protect(int64) bool {
	p.calls.Add(1)
	if p.panicNow {
		panic("test")
	}
	return p.accept.Load()
}

// Host fixture installs a real native GLOBAL proxy graph and the actual mobile
// handlers, but never forges StartAndroid/TUN readiness on a non-Android host.
func mobileFixture(t *testing.T) (*mobileSession, *mobileProtector) {
	return mobileFixtureSource(t, mobileConfig)
}

func mobileFixtureSource(t *testing.T, source string) (*mobileSession, *mobileProtector) {
	t.Helper()
	stopTest(t)
	p := &mobileProtector{}
	p.accept.Store(true)
	SetSocketProtector(p)
	ctx, cancel := context.WithCancel(context.Background())
	m := newMobileSession(ctx, cancel)
	d, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	if err = mobilePolicy(d); err != nil {
		t.Fatal(err)
	}
	cfg, err := nativeParse(d)
	if err != nil {
		t.Fatal(err)
	}
	m.cfg = cfg
	oldDNS := saveDNS()
	m.installDNS(d)
	singleton.mu.Lock()
	singleton.state = "running"
	singleton.session = &session{ctx: ctx, cancel: cancel, mobile: m, cfg: cfg, doc: d}
	singleton.mu.Unlock()
	t.Cleanup(func() {
		m.close()
		closeConfig(cfg)
		oldDNS.restore()
		singleton.mu.Lock()
		singleton.state = "stopped"
		singleton.session = nil
		singleton.mu.Unlock()
		SetSocketProtector(nil)
	})
	return m, p
}

func TestMobileAdmissionDoesNotBroadenDesktop(t *testing.T) {
	d, e := inspect(mobileConfig)
	if e != nil || len(d.StrictIssues) > 0 {
		t.Fatal(e, d.StrictIssues)
	}
	if e = mobilePolicy(d); e != nil {
		t.Fatal(e)
	}
	vmessYAML := strings.Replace(mobileConfig, "type: direct", "type: vmess, server: 192.0.2.10, port: 443, uuid: 00000000-0000-0000-0000-000000000001, alterId: 0, cipher: auto", 1)
	vmessDoc, err := inspect(vmessYAML)
	if err != nil || len(vmessDoc.StrictIssues) != 0 || mobilePolicy(vmessDoc) != nil {
		t.Fatalf("TCP VMess should pass bounded mobile admission: inspect=%v strict=%v policy=%v", err, vmessDoc.StrictIssues, mobilePolicy(vmessDoc))
	}
	vmessWSYAML := strings.Replace(vmessYAML, "cipher: auto", "cipher: auto, network: ws, ws-opts: {path: /nimbo, headers: {X-Nimbo: audited}}", 1)
	vmessWSDoc, err := inspect(vmessWSYAML)
	if err != nil || len(vmessWSDoc.StrictIssues) != 0 || mobilePolicy(vmessWSDoc) != nil {
		t.Fatalf("bounded VMess WebSocket/TCP should pass mobile admission: inspect=%v strict=%v policy=%v", err, vmessWSDoc.StrictIssues, mobilePolicy(vmessWSDoc))
	}
	for _, change := range []struct{ from, to string }{
		{"mode: global", "mode: global\nrules: [MATCH,DIRECT]"},
		{"type: direct", "type: hysteria2"}, {"type: select", "type: url-test"},
		{"127.0.0.1:5353", "system"}, {"127.0.0.1:5353", "dhcp://eth0"}, {"127.0.0.1:5353", "https://1.1.1.1/dns-query"},
		{"enable: true", "enable: false"},
	} {
		d, e := inspect(strings.Replace(mobileConfig, change.from, change.to, 1))
		if e == nil && mobilePolicy(d) == nil {
			t.Errorf("accepted %s", change.to)
		}
	}
	// Source transport unsupported on mobile still remains inspectable and the
	// desktop validation path keeps its original schema.
	source := "proxies: [{name: x, type: vless, server: example.com, port: 443, uuid: 00000000-0000-0000-0000-000000000001}]\n"
	d, e = inspect(source)
	if e != nil || len(d.StrictIssues) > 0 || d.OriginalYAML != source {
		t.Fatal("desktop source policy changed")
	}
	parser, e := strictProxyParser("mobile", map[string]any{}, mobileProxyPolicy)
	if e != nil {
		t.Fatal(e)
	}
	if ps, e := parser([]byte(strings.Replace(source, "uuid:", "network: grpc, uuid:", 1))); e == nil {
		for _, p := range ps {
			p.Close()
		}
		t.Fatal("provider refresh bypassed mobile admission")
	}
}

func TestAndroidTunPlanListsVMessTCPAndWebSocketWithoutClaimingDeviceReadiness(t *testing.T) {
	var plan struct {
		Success bool `json:"success"`
		Data    struct {
			Protocols      string `json:"protocols"`
			TransportScope string `json:"transportScope"`
			DeviceVerified bool   `json:"deviceVerified"`
		} `json:"data"`
	}
	if err := json.Unmarshal([]byte(AndroidTunPlan()), &plan); err != nil {
		t.Fatal(err)
	}
	if !plan.Success || plan.Data.DeviceVerified {
		t.Fatalf("plan overstates runtime readiness: %+v", plan)
	}
	if !strings.Contains(plan.Data.Protocols, "pinned Mihomo v1.19.32 upstream outbound parser") {
		t.Fatalf("upstream protocol scope not reported: %q", plan.Data.Protocols)
	}
	if !strings.Contains(plan.Data.TransportScope, "proxy/protocol implementations") {
		t.Fatalf("upstream transport scope not reported: %q", plan.Data.TransportScope)
	}
}
func TestMobileTrustedEntryCannotUseDesktopOrUnknownFDJSON(t *testing.T) {
	var r testReply
	for _, input := range []string{`{"apiVersion":1,"operation":"start","borrowedFD":12}`, `{"apiVersion":1,"operation":"status"}`} {
		_ = json.Unmarshal([]byte(StartAndroid(input, 12)), &r)
		if r.Success {
			t.Fatal("trusted entry accepted invalid request")
		}
	}
	_ = json.Unmarshal([]byte(StartAndroid(`{"apiVersion":1,"operation":"start","options":{"networkOwner":"desktop-proxy"}}`, 12)), &r)
	if r.Success || r.Error.Code != "INVALID_REQUEST" {
		t.Fatal(r)
	}
	_ = json.Unmarshal([]byte(Invoke(`{"apiVersion":1,"operation":"start","options":{"networkOwner":"android-vpn"}}`)), &r)
	if r.Success || r.Error.Code != "PLATFORM_UNAVAILABLE" {
		t.Fatal(r)
	}
}
func TestMobileMissingFalsePanicProtectionBlocksTCPUDPProvider(t *testing.T) {
	m, p := mobileFixture(t)
	server := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { t.Error("unprotected provider request escaped") }))
	defer server.Close()
	for _, mode := range []string{"nil", "false", "panic"} {
		t.Run(mode, func(t *testing.T) {
			p.accept.Store(false)
			p.panicNow = mode == "panic"
			protectorLock.Lock()
			socketProtector = p
			if mode == "nil" {
				socketProtector = nil
			}
			protectorLock.Unlock()
			ctx, cancel := context.WithTimeout(m.ctx, time.Second)
			defer cancel()
			if c, e := dialer.DialContext(ctx, "tcp4", strings.TrimPrefix(server.URL, "http://")); e == nil {
				c.Close()
				t.Fatal("TCP protection bypass")
			}
			if c, e := dialer.ListenPacket(ctx, "udp4", "", netip.MustParseAddrPort("127.0.0.1:53")); e == nil {
				c.Close()
				t.Fatal("UDP protection bypass")
			}
			v := managedVehicle{kind: P.HTTP, url: server.URL}
			if _, _, e := v.Read(ctx, utils.HashType{}); e == nil {
				t.Fatal("provider protection bypass")
			}
		})
	}
}
func TestMobileTCPRelayAndJoin(t *testing.T) {
	m, p := mobileFixture(t)
	listener, e := net.Listen("tcp4", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	defer listener.Close()
	serverDone := make(chan struct{})
	go func() {
		defer close(serverDone)
		c, e := listener.Accept()
		if e == nil {
			defer c.Close()
			_, _ = io.Copy(c, c)
		}
	}()
	local, app := net.Pipe()
	defer app.Close()
	ap := netip.MustParseAddrPort(listener.Addr().String())
	done := make(chan error, 1)
	go func() {
		done <- m.NewConnection(m.ctx, local, M.Metadata{Source: M.ParseSocksaddr("172.19.0.1:10000"), Destination: M.SocksaddrFrom(ap.Addr(), ap.Port())})
	}()
	_ = app.SetDeadline(time.Now().Add(3 * time.Second))
	if _, e = app.Write([]byte("mobile-real-tcp")); e != nil {
		t.Fatal(e)
	}
	b := make([]byte, 15)
	if _, e = io.ReadFull(app, b); e != nil || string(b) != "mobile-real-tcp" {
		t.Fatal(string(b), e)
	}
	m.close()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("relay not joined")
	}
	<-serverDone
	if p.calls.Load() == 0 {
		t.Fatal("no protector call")
	}
}

func TestMobileVMessTCPRelayStopsAndJoins(t *testing.T) {
	m, protector := mobileFixture(t)
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	ap := netip.MustParseAddrPort(listener.Addr().String())
	raw := map[string]any{
		"name": "vmess-mobile", "type": "vmess", "server": "127.0.0.1", "port": int(ap.Port()),
		"uuid": "00000000-0000-0000-0000-000000000001", "alterId": 0, "cipher": "auto",
	}
	if err = mobileProxyPolicy(raw, "proxy"); err != nil {
		t.Fatal(err)
	}
	proxy, err := adapter.ParseProxy(raw)
	if err != nil {
		t.Fatal(err)
	}
	if err = m.cfg.Proxies["GLOBAL"].Close(); err != nil {
		t.Fatal(err)
	}
	m.cfg.Proxies["GLOBAL"] = proxy
	accepted := make(chan net.Conn, 1)
	go func() {
		c, acceptErr := listener.Accept()
		if acceptErr == nil {
			accepted <- c
		}
	}()
	local, app := net.Pipe()
	defer app.Close()
	done := make(chan error, 1)
	go func() {
		done <- m.NewConnection(m.ctx, local, M.Metadata{
			Source: M.ParseSocksaddr("172.19.0.1:10002"), Destination: M.ParseSocksaddr("203.0.113.8:443"),
		})
	}()
	var serverConn net.Conn
	select {
	case serverConn = <-accepted:
	case <-time.After(2 * time.Second):
		t.Fatal("VMess TCP dial did not reach the protected test listener")
	}
	defer serverConn.Close()
	_ = serverConn.SetReadDeadline(time.Now().Add(2 * time.Second))
	oneByte := make([]byte, 1)
	if _, err = io.ReadFull(serverConn, oneByte); err != nil {
		t.Fatal("VMess request handshake was not written", err)
	}
	if protector.calls.Load() == 0 {
		t.Fatal("VMess outbound TCP was not protected")
	}
	m.close()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("VMess relay did not join on session stop")
	}
	m.mu.Lock()
	sockets := len(m.sockets)
	m.mu.Unlock()
	if sockets != 0 {
		t.Fatalf("VMess session leaked %d sockets", sockets)
	}
}

func TestMobileVMessWebSocketTCPRelayStopsAndJoins(t *testing.T) {
	m, protector := mobileFixture(t)
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	endpoint := netip.MustParseAddrPort(listener.Addr().String())
	raw := map[string]any{
		"name": "vmess-ws-mobile", "type": "vmess", "server": "127.0.0.1", "port": int(endpoint.Port()),
		"uuid": "00000000-0000-0000-0000-000000000001", "alterId": 0, "cipher": "auto", "network": "ws",
		"ws-opts": map[string]any{"path": "/nimbo", "headers": map[string]any{"X-Nimbo": "audited"}},
	}
	if err = mobileProxyPolicy(raw, "proxy"); err != nil {
		t.Fatal(err)
	}
	proxy, err := adapter.ParseProxy(raw)
	if err != nil {
		t.Fatal(err)
	}
	defer proxy.Close()
	if err = m.cfg.Proxies["GLOBAL"].Close(); err != nil {
		t.Fatal(err)
	}
	m.cfg.Proxies["GLOBAL"] = proxy
	accepted := make(chan net.Conn, 1)
	serverErr := make(chan error, 1)
	go func() {
		conn, acceptErr := listener.Accept()
		if acceptErr != nil {
			serverErr <- acceptErr
			return
		}
		reader := bufio.NewReader(conn)
		request, readErr := http.ReadRequest(reader)
		if readErr != nil {
			_ = conn.Close()
			serverErr <- readErr
			return
		}
		if request.URL.Path != "/nimbo" || request.Header.Get("X-Nimbo") != "audited" {
			_ = conn.Close()
			serverErr <- fmt.Errorf("unexpected WebSocket request path=%q header=%q", request.URL.Path, request.Header.Get("X-Nimbo"))
			return
		}
		key := request.Header.Get("Sec-WebSocket-Key")
		if key == "" {
			_ = conn.Close()
			serverErr <- fmt.Errorf("missing WebSocket key")
			return
		}
		acceptHash := sha1.Sum([]byte(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
		accept := base64.StdEncoding.EncodeToString(acceptHash[:])
		if _, writeErr := fmt.Fprintf(conn, "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Accept: %s\r\n\r\n", accept); writeErr != nil {
			_ = conn.Close()
			serverErr <- writeErr
			return
		}
		accepted <- conn
	}()
	local, app := net.Pipe()
	defer app.Close()
	done := make(chan error, 1)
	go func() {
		done <- m.NewConnection(m.ctx, local, M.Metadata{
			Source: M.ParseSocksaddr("172.19.0.1:10003"), Destination: M.ParseSocksaddr("203.0.113.9:443"),
		})
	}()
	var serverConn net.Conn
	select {
	case serverConn = <-accepted:
	case err = <-serverErr:
		t.Fatal("VMess WebSocket handshake failed", err)
	case <-time.After(2 * time.Second):
		t.Fatal("VMess WebSocket did not reach the protected listener")
	}
	defer serverConn.Close()
	_ = serverConn.SetReadDeadline(time.Now().Add(2 * time.Second))
	frame := make([]byte, 2)
	if _, err = io.ReadFull(serverConn, frame); err != nil {
		t.Fatal("VMess handshake did not enter the WebSocket stream", err)
	}
	if frame[0]&0x0f != 2 || frame[1]&0x80 == 0 {
		t.Fatalf("expected a masked binary client frame, got %x", frame)
	}
	if protector.calls.Load() == 0 {
		t.Fatal("VMess WebSocket socket was not protected")
	}
	m.close()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("VMess WebSocket relay did not join on session stop")
	}
	m.mu.Lock()
	sockets := len(m.sockets)
	m.mu.Unlock()
	if sockets != 0 {
		t.Fatalf("VMess WebSocket session leaked %d sockets", sockets)
	}
}

func TestMobileVMessWebSocketTLSRelayStopsAndJoins(t *testing.T) {
	m, protector := mobileFixture(t)
	accepted := make(chan net.Conn, 1)
	serverErr := make(chan error, 1)
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/secure-nimbo" {
			serverErr <- fmt.Errorf("unexpected TLS WebSocket path %q", r.URL.Path)
			return
		}
		key := r.Header.Get("Sec-WebSocket-Key")
		if key == "" {
			serverErr <- fmt.Errorf("missing TLS WebSocket key")
			return
		}
		hijacker, ok := w.(http.Hijacker)
		if !ok {
			serverErr <- fmt.Errorf("TLS WebSocket server does not support hijacking")
			return
		}
		conn, rw, err := hijacker.Hijack()
		if err != nil {
			serverErr <- err
			return
		}
		acceptHash := sha1.Sum([]byte(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
		accept := base64.StdEncoding.EncodeToString(acceptHash[:])
		if _, err = fmt.Fprintf(rw, "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Accept: %s\r\n\r\n", accept); err == nil {
			err = rw.Flush()
		}
		if err != nil {
			_ = conn.Close()
			serverErr <- err
			return
		}
		accepted <- conn
	}))
	defer server.Close()
	endpoint := server.Listener.Addr().(*net.TCPAddr)
	raw := map[string]any{
		"name": "vmess-wss-mobile", "type": "vmess", "server": endpoint.IP.String(), "port": endpoint.Port,
		"uuid": "00000000-0000-0000-0000-000000000001", "alterId": 0, "cipher": "auto", "network": "ws",
		"tls": true, "skip-cert-verify": true,
		"ws-opts": map[string]any{"path": "/secure-nimbo"},
	}
	if err := mobileProxyPolicy(raw, "proxy"); err != nil {
		t.Fatal(err)
	}
	proxy, err := adapter.ParseProxy(raw)
	if err != nil {
		t.Fatal(err)
	}
	defer proxy.Close()
	if err = m.cfg.Proxies["GLOBAL"].Close(); err != nil {
		t.Fatal(err)
	}
	m.cfg.Proxies["GLOBAL"] = proxy
	local, app := net.Pipe()
	defer app.Close()
	done := make(chan error, 1)
	go func() {
		done <- m.NewConnection(m.ctx, local, M.Metadata{
			Source: M.ParseSocksaddr("172.19.0.1:10004"), Destination: M.ParseSocksaddr("203.0.113.10:443"),
		})
	}()
	var serverConn net.Conn
	select {
	case serverConn = <-accepted:
	case err = <-serverErr:
		t.Fatal("VMess TLS WebSocket handshake failed", err)
	case <-time.After(3 * time.Second):
		t.Fatal("VMess TLS WebSocket did not reach the protected listener")
	}
	defer serverConn.Close()
	_ = serverConn.SetReadDeadline(time.Now().Add(2 * time.Second))
	frame := make([]byte, 2)
	if _, err = io.ReadFull(serverConn, frame); err != nil {
		t.Fatal("VMess handshake did not enter the TLS WebSocket stream", err)
	}
	if frame[0]&0x0f != 2 || frame[1]&0x80 == 0 {
		t.Fatalf("expected a masked binary TLS WebSocket client frame, got %x", frame)
	}
	if protector.calls.Load() == 0 {
		t.Fatal("VMess TLS WebSocket socket was not protected")
	}
	m.close()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("VMess TLS WebSocket relay did not join on session stop")
	}
	m.mu.Lock()
	sockets := len(m.sockets)
	m.mu.Unlock()
	if sockets != 0 {
		t.Fatalf("VMess TLS WebSocket session leaked %d sockets", sockets)
	}
}

type capturePacketWriter struct{ replies chan []byte }

func (w *capturePacketWriter) WritePacket(b *buf.Buffer, _ M.Socksaddr) error {
	defer b.Release()
	select {
	case w.replies <- append([]byte(nil), b.Bytes()...):
	default:
	}
	return nil
}
func TestMobileUDPRelayDNSAndStop(t *testing.T) {
	m, p := mobileFixture(t)
	pc, e := net.ListenPacket("udp4", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	defer pc.Close()
	stopEcho := make(chan struct{})
	go func() {
		defer close(stopEcho)
		b := make([]byte, 2048)
		for {
			n, a, e := pc.ReadFrom(b)
			if e != nil {
				return
			}
			_, _ = pc.WriteTo(b[:n], a)
		}
	}()
	w := &capturePacketWriter{make(chan []byte, 8)}
	ap := netip.MustParseAddrPort(pc.LocalAddr().String())
	md := M.Metadata{Source: M.ParseSocksaddr("172.19.0.1:10001"), Destination: M.SocksaddrFrom(ap.Addr(), ap.Port())}
	m.NewPacket(m.ctx, md.Source.AddrPort(), buf.As([]byte("real-udp")), md, func(N.PacketConn) N.PacketWriter { return w })
	select {
	case b := <-w.replies:
		if string(b) != "real-udp" {
			t.Fatal(string(b))
		}
	case <-time.After(3 * time.Second):
		t.Fatal("UDP did not relay")
	}
	q := new(D.Msg)
	q.SetQuestion("example.test.", D.TypeAAAA)
	raw, _ := q.Pack()
	md.Destination = M.ParseSocksaddr("172.19.0.2:53")
	m.NewPacket(m.ctx, md.Source.AddrPort(), buf.As(raw), md, func(N.PacketConn) N.PacketWriter { return w })
	select {
	case b := <-w.replies:
		r := new(D.Msg)
		if e = r.Unpack(b); e != nil || len(r.Answer) != 0 {
			t.Fatal("IPv6 advertised", e)
		}
	case <-time.After(time.Second):
		t.Fatal("managed DNS did not answer")
	}
	m.close()
	pc.Close()
	<-stopEcho
	m.mu.Lock()
	flows, sockets := len(m.flows), len(m.sockets)
	m.mu.Unlock()
	if flows != 0 || sockets != 0 {
		t.Fatalf("leaked flows=%d sockets=%d", flows, sockets)
	}
	if p.calls.Load() == 0 {
		t.Fatal("UDP unprotected")
	}
}

func TestMobileRuleRoutingUsesNativeRulesAndFailsClosed(t *testing.T) {
	d, err := inspect(mobileRuleConfig)
	if err != nil {
		t.Fatal(err)
	}
	if err = mobilePolicy(d); err != nil {
		t.Fatalf("supported rule config rejected: %v", err)
	}
	cfg, err := nativeParse(d)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	m := newMobileSession(ctx, cancel)
	m.cfg = cfg
	t.Cleanup(func() {
		m.close()
		closeConfig(cfg)
	})

	for _, tc := range []struct {
		port uint16
		want string
	}{{443, "port-443"}, {80, "fallback"}} {
		got, routeErr := m.routeProxy(&C.Metadata{
			NetWork: C.TCP,
			Type:    C.TUN,
			DstIP:   netip.MustParseAddr("203.0.113.10"),
			DstPort: tc.port,
		})
		if routeErr != nil || got == nil || got.Name() != tc.want {
			t.Fatalf("port %d selected %v, err=%v; want %s", tc.port, got, routeErr, tc.want)
		}
	}
	if got, routeErr := m.routeProxy(&C.Metadata{
		NetWork: C.UDP,
		Type:    C.TUN,
		DstIP:   netip.MustParseAddr("203.0.113.10"),
		DstPort: 443,
	}); routeErr != nil || got == nil || got.Name() != "port-443" {
		t.Fatalf("UDP rule dispatch selected %v, err=%v", got, routeErr)
	}

	// A missing/partial rule graph must never inherit Mihomo's implicit DIRECT.
	m.cfg.Rules = nil
	if got, routeErr := m.routeProxy(&C.Metadata{NetWork: C.TCP, DstIP: netip.MustParseAddr("203.0.113.10"), DstPort: 80}); routeErr == nil || got != nil {
		t.Fatalf("unmatched mobile flow did not fail closed: proxy=%v err=%v", got, routeErr)
	}
}

func TestMobileApplicationDNSFollowsNativeRuleGraph(t *testing.T) {
	m, p := mobileFixture(t)
	hits := applicationDNSProxy(t, m)
	// Replace the global-mode fixture route with a real Mihomo MATCH rule and
	// route DNS to a named outbound that is only reachable through HTTP CONNECT.
	m.cfg.Proxies["dns-via-http"] = m.cfg.Proxies["GLOBAL"]
	rule, err := rules.ParseRule("MATCH", "", "dns-via-http", nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	m.cfg.Rules = []C.Rule{rule}
	m.cfg.General.Mode = tunnel.Rule

	q := new(D.Msg)
	q.SetQuestion("rule-dns.test.", D.TypeA)
	if _, err = m.dns.ExchangeApplicationContext(m.ctx, q); err != nil {
		t.Fatal("managed application DNS did not use the session's rule graph:", err)
	}
	if hits.Load() != 1 || p.calls.Load() == 0 {
		t.Fatalf("rule-routed application DNS bypassed selected protected proxy: hits=%d protect=%d", hits.Load(), p.calls.Load())
	}
}

func TestMobileRuleAdmissionRejectsUnownedRuleFeatures(t *testing.T) {
	for name, source := range map[string]string{
		"domain rules need per-flow host attribution": strings.Replace(mobileRuleConfig, "DST-PORT,443,port-443", "DOMAIN,example.test,port-443", 1),
		"unmatched tail could default direct":         strings.Replace(mobileRuleConfig, "  - MATCH,fallback\n", "", 1),
		"subrules are not session-dispatched":         strings.Replace(mobileRuleConfig, "rules:\n", "sub-rules: {local: [MATCH,port-443]}\nrules:\n", 1),
	} {
		t.Run(name, func(t *testing.T) {
			d, err := inspect(source)
			if err != nil {
				t.Fatal(err)
			}
			if err = mobilePolicy(d); err == nil {
				t.Fatal("unsafe or unsupported rule graph was admitted")
			}
		})
	}
}

func TestMobileRuleSetUsesManagedNativeProvider(t *testing.T) {
	stopTest(t)
	source := `mode: rule
dns:
  enable: true
  nameserver: [127.0.0.1:5353]
proxies:
  - {name: matched, type: direct}
  - {name: fallback, type: direct}
rule-providers:
  ports:
    type: inline
    behavior: classical
    payload: ["DST-PORT,443"]
rules:
  - RULE-SET,ports,matched
  - MATCH,fallback
`
	d, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	if err = mobilePolicy(d); err != nil {
		t.Fatal("managed inline rule provider rejected:", err)
	}
	d.mobile = true
	cfg, err := nativeParse(d)
	if err != nil {
		t.Fatal(err)
	}
	if err = rebindRuleProviders(cfg, d, t.TempDir()); err != nil {
		closeConfig(cfg)
		t.Fatal(err)
	}
	tunnel.UpdateRules(cfg.Rules, cfg.SubRules, cfg.RuleProviders)
	ctx, cancel := context.WithCancel(context.Background())
	m := newMobileSession(ctx, cancel)
	m.cfg = cfg
	t.Cleanup(func() {
		m.close()
		closeConfig(cfg)
	})

	for _, tc := range []struct {
		port uint16
		want string
	}{{443, "matched"}, {80, "fallback"}} {
		got, routeErr := m.routeProxy(&C.Metadata{
			NetWork: C.TCP,
			Type:    C.TUN,
			DstIP:   netip.MustParseAddr("203.0.113.10"),
			DstPort: tc.port,
		})
		if routeErr != nil || got == nil || got.Name() != tc.want {
			t.Fatalf("rule-provider dispatch port=%d proxy=%v err=%v; want %s", tc.port, got, routeErr, tc.want)
		}
	}
}

func TestMobileDNSProtectedNumericUpstream(t *testing.T) {
	m, p := mobileFixture(t)
	pc, e := net.ListenPacket("udp4", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	server := &D.Server{PacketConn: pc, Handler: D.HandlerFunc(func(w D.ResponseWriter, q *D.Msg) {
		r := new(D.Msg)
		r.SetReply(q)
		rr, _ := D.NewRR(q.Question[0].Name + " 30 IN A 192.0.2.25")
		r.Answer = []D.RR{rr}
		_ = w.WriteMsg(r)
	})}
	go server.ActivateAndServe()
	defer server.Shutdown()
	m.dns.servers = []string{pc.LocalAddr().String()}
	ips, e := m.dns.LookupIPv4(m.ctx, "example.test")
	if e != nil || len(ips) != 1 || ips[0].String() != "192.0.2.25" {
		t.Fatal(ips, e)
	}
	if p.calls.Load() == 0 {
		t.Fatal("DNS bypassed protection")
	}
	p.accept.Store(false)
	if _, e = m.dns.LookupIPv4(m.ctx, "blocked.test"); e == nil {
		t.Fatal("DNS bypassed rejection")
	}
}
func TestMobileProviderRefreshRejectsUnauditedTransports(t *testing.T) {
	parser, e := strictProxyParser("mobile", map[string]any{}, mobileProxyPolicy)
	if e != nil {
		t.Fatal(e)
	}
	for _, typ := range []string{"vless", "hysteria2", "tuic", "ss"} {
		_, e = parser([]byte(fmt.Sprintf("proxies: [{name: p, type: %s, server: 127.0.0.1, port: 1234}]", typ)))
		if e == nil {
			t.Fatal("accepted", typ)
		}
	}
	wsProxies, e := parser([]byte("proxies:\n  - name: vmess-ws-provider\n    type: vmess\n    server: 127.0.0.1\n    port: 443\n    uuid: 00000000-0000-0000-0000-000000000001\n    alterId: 0\n    cipher: auto\n    network: ws\n    ws-opts:\n      path: /nimbo\n      headers:\n        X-Nimbo: audited\n"))
	if e != nil || len(wsProxies) != 1 {
		t.Fatalf("audited VMess WebSocket provider row should parse, proxies=%d err=%v", len(wsProxies), e)
	}
	for _, proxy := range wsProxies {
		proxy.Close()
	}
	// A real audited proxy still constructs through the pinned native adapter.
	p, e := adapter.ParseProxy(map[string]any{"name": "direct-test", "type": "direct"})
	if e != nil {
		t.Fatal(e)
	}
	p.Close()
	var _ C.Proxy = p
}

func TestMobileOneShotAutoSelectPicksHealthyProtectedNodeAndDoesNotLoop(t *testing.T) {
	m, protector := mobileFixture(t)
	var targetStatus atomic.Int32
	targetStatus.Store(http.StatusNoContent)
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodHead {
			t.Errorf("health probe method = %s, want HEAD", r.Method)
		}
		w.WriteHeader(int(targetStatus.Load()))
	}))
	defer target.Close()

	newHTTPProxy := func(delay time.Duration) *httptest.Server {
		t.Helper()
		return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != http.MethodConnect {
				t.Errorf("outbound probe did not use protected HTTP CONNECT: %s", r.Method)
				w.WriteHeader(http.StatusMethodNotAllowed)
				return
			}
			if delay > 0 {
				time.Sleep(delay)
			}
			upstream, err := net.DialTimeout("tcp", r.Host, time.Second)
			if err != nil {
				http.Error(w, "test upstream unavailable", http.StatusBadGateway)
				return
			}
			client, rw, err := w.(http.Hijacker).Hijack()
			if err != nil {
				_ = upstream.Close()
				return
			}
			if _, err = rw.WriteString("HTTP/1.1 200 Connection established\r\n\r\n"); err == nil {
				err = rw.Flush()
			}
			if err != nil {
				_ = client.Close()
				_ = upstream.Close()
				return
			}
			go func() { _, _ = io.Copy(upstream, client); _ = upstream.Close() }()
			_, _ = io.Copy(client, upstream)
			_ = client.Close()
			_ = upstream.Close()
		}))
	}
	slow := newHTTPProxy(120 * time.Millisecond)
	defer slow.Close()
	fast := newHTTPProxy(0)
	defer fast.Close()
	endpoint := func(server *httptest.Server) netip.AddrPort {
		t.Helper()
		return netip.MustParseAddrPort(strings.TrimPrefix(server.URL, "http://"))
	}
	slowAddress, fastAddress := endpoint(slow), endpoint(fast)
	source := fmt.Sprintf(`mode: global
dns:
  enable: true
  nameserver: [127.0.0.1:5353]
proxies:
  - {name: slow, type: http, server: %s, port: %d}
  - {name: fast, type: http, server: %s, port: %d}
proxy-groups:
  - {name: GLOBAL, type: select, proxies: [slow, DIRECT, fast], default-selected: slow, empty-fallback: REJECT}
`, slowAddress.Addr(), slowAddress.Port(), fastAddress.Addr(), fastAddress.Port())
	doc, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	if err = mobilePolicy(doc); err != nil {
		t.Fatal(err)
	}
	cfg, err := nativeParse(doc)
	if err != nil {
		t.Fatal(err)
	}
	defer closeConfig(cfg)
	m.cfg = cfg
	singleton.mu.Lock()
	singleton.session.cfg = cfg
	singleton.session.doc = doc
	singleton.mu.Unlock()

	fields := map[string]any{"group": "GLOBAL", "url": target.URL + "/generate_204", "timeoutMs": 2000, "expectedStatus": "204"}
	selected := call(t, "autoSelect", fields)
	requireOK(t, selected)
	var result struct {
		Selected string `json:"selected"`
		DelayMs  uint16 `json:"delayMs"`
		Probed   int    `json:"probed"`
	}
	if err = json.Unmarshal(selected.Data, &result); err != nil {
		t.Fatal(err)
	}
	if result.Selected != "fast" || result.Probed != 2 {
		t.Fatalf("one-shot auto selection did not choose fastest healthy node: %+v", result)
	}
	group := cfg.Proxies["GLOBAL"].Adapter().(interface{ Now() string })
	if group.Now() != "fast" || protector.calls.Load() < 2 {
		t.Fatalf("selection did not persist or probes escaped socket protection: selected=%s protectCalls=%d", group.Now(), protector.calls.Load())
	}
	if snapshot := call(t, "snapshot", nil); !snapshot.Success || group.Now() != "fast" {
		t.Fatalf("polling changed the one-shot choice: %+v current=%s", snapshot.Error, group.Now())
	}

	// A later explicit pick with failed probes must keep the last known working
	// route. There is no implicit switch to DIRECT and no continuous test loop.
	targetStatus.Store(http.StatusServiceUnavailable)
	failed := call(t, "autoSelect", fields)
	if failed.Success || failed.Error == nil || failed.Error.Code != "NO_HEALTHY_PROXY" || group.Now() != "fast" {
		t.Fatalf("failed auto selection must preserve the current node: reply=%+v current=%s", failed, group.Now())
	}
}

func TestMobileNativeAutomaticGroupsProbeProtectedProviderNodes(t *testing.T) {
	m, protector := mobileFixture(t)
	var hits atomic.Int64
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodHead {
			t.Errorf("automatic health probe method = %s, want HEAD", r.Method)
		}
		hits.Add(1)
		w.WriteHeader(http.StatusNoContent)
	}))
	defer target.Close()

	newHTTPProxy := func(delay time.Duration, connects *atomic.Int64) *httptest.Server {
		t.Helper()
		return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != http.MethodConnect {
				t.Errorf("automatic health probe escaped HTTP CONNECT: %s", r.Method)
				w.WriteHeader(http.StatusMethodNotAllowed)
				return
			}
			connects.Add(1)
			if delay > 0 {
				time.Sleep(delay)
			}
			upstream, err := net.DialTimeout("tcp", r.Host, time.Second)
			if err != nil {
				http.Error(w, "test upstream unavailable", http.StatusBadGateway)
				return
			}
			client, rw, err := w.(http.Hijacker).Hijack()
			if err != nil {
				_ = upstream.Close()
				return
			}
			if _, err = rw.WriteString("HTTP/1.1 200 Connection established\r\n\r\n"); err == nil {
				err = rw.Flush()
			}
			if err != nil {
				_ = client.Close()
				_ = upstream.Close()
				return
			}
			go func() { _, _ = io.Copy(upstream, client); _ = upstream.Close() }()
			_, _ = io.Copy(client, upstream)
			_ = client.Close()
			_ = upstream.Close()
		}))
	}
	var slowConnects, fastConnects atomic.Int64
	slow, fast := newHTTPProxy(100*time.Millisecond, &slowConnects), newHTTPProxy(0, &fastConnects)
	defer slow.Close()
	defer fast.Close()
	endpoint := func(server *httptest.Server) netip.AddrPort {
		t.Helper()
		return netip.MustParseAddrPort(strings.TrimPrefix(server.URL, "http://"))
	}
	slowAddress, fastAddress := endpoint(slow), endpoint(fast)
	healthURL := target.URL + "/generate_204"
	source := fmt.Sprintf(`mode: global
dns:
  enable: true
  nameserver: [127.0.0.1:5353]
proxy-providers:
  Nodes:
    type: inline
    payload:
      - {name: slow, type: http, server: %s, port: %d}
      - {name: fast, type: http, server: %s, port: %d}
proxy-groups:
  - {name: Auto, type: url-test, use: [Nodes], url: %q, interval: 3600, timeout: 2000, empty-fallback: REJECT}
  - {name: Failover, type: fallback, use: [Nodes], url: %q, interval: 3600, timeout: 2000, empty-fallback: REJECT}
  - {name: Balanced, type: load-balance, use: [Nodes], url: %q, interval: 3600, timeout: 2000, strategy: round-robin, empty-fallback: REJECT}
  - {name: GLOBAL, type: select, proxies: [Auto, Failover, Balanced], default-selected: Auto, empty-fallback: REJECT}
`, slowAddress.Addr(), slowAddress.Port(), fastAddress.Addr(), fastAddress.Port(), healthURL, healthURL, healthURL)
	doc, err := inspect(source)
	if err != nil {
		t.Fatal(err)
	}
	if err = mobilePolicy(doc); err != nil {
		t.Fatalf("safe native automatic groups rejected: %v", err)
	}
	cfg, err := nativeParse(doc)
	if err != nil {
		t.Fatal(err)
	}
	if err = rebindProviders(cfg, doc, t.TempDir()); err != nil {
		t.Fatal(err)
	}
	defer closeConfig(cfg)
	m.cfg = cfg
	singleton.mu.Lock()
	singleton.session.cfg = cfg
	singleton.session.doc = doc
	singleton.mu.Unlock()
	for _, provider := range cfg.Providers {
		if err = provider.Initial(); err != nil {
			t.Fatal(err)
		}
	}

	auto := cfg.Proxies["Auto"].Adapter().(interface{ Now() string })
	candidates := cfg.Proxies["Auto"].Adapter().(interface{ Proxies() []C.Proxy }).Proxies()
	deadline := time.Now().Add(5 * time.Second)
	for (hits.Load() < 2 || slowConnects.Load() < 1 || fastConnects.Load() < 1) && time.Now().Before(deadline) {
		time.Sleep(10 * time.Millisecond)
	}
	var measured []string
	for _, candidate := range candidates {
		measured = append(measured, fmt.Sprintf("%s alive=%t delay=%d", candidate.Name(), candidate.AliveForTestUrl(healthURL), candidate.LastDelayForTestUrl(healthURL)))
	}
	selected := auto.Now()
	if hits.Load() < 2 || slowConnects.Load() < 1 || fastConnects.Load() < 1 || protector.calls.Load() < 2 || (selected != "slow" && selected != "fast") {
		t.Fatalf("native url-test did not probe both provider nodes through protected sockets: now=%q candidates=%v healthRequests=%d connectRequests=[slow:%d fast:%d] protectCalls=%d",
			selected, measured, hits.Load(), slowConnects.Load(), fastConnects.Load(), protector.calls.Load())
	}
	snapshotReply := call(t, "snapshot", nil)
	requireOK(t, snapshotReply)
	var snapshotData struct {
		Groups map[string]struct {
			Type string `json:"type"`
		} `json:"groups"`
	}
	if err = json.Unmarshal(snapshotReply.Data, &snapshotData); err != nil {
		t.Fatal(err)
	}
	for name, want := range map[string]string{"Auto": "urltest", "Failover": "fallback", "Balanced": "loadbalance"} {
		if got := strings.ToLower(snapshotData.Groups[name].Type); got != want {
			t.Fatalf("native group not represented in snapshot: %s=%q, want %q", name, got, want)
		}
		if call(t, "select", map[string]any{"group": name, "name": "fast"}).Success {
			t.Fatalf("automatic %s group was exposed as manually selectable", name)
		}
	}
}

func TestMobileAuditedProtocolsProtectBeforeTCPAndUDP(t *testing.T) {
	m, p := mobileFixture(t)
	p.accept.Store(false)
	for _, typ := range []string{"direct", "socks5", "http", "vmess", "vmess-ws", "vless", "vless-reality", "trojan", "ss"} {
		t.Run(typ, func(t *testing.T) {
			protocol := typ
			if typ == "vless-reality" {
				protocol = "vless"
			} else if typ == "vmess-ws" {
				protocol = "vmess"
			}
			raw := map[string]any{"name": "guarded", "type": protocol, "server": "127.0.0.1", "port": 34567}
			if protocol == "vless" {
				raw["uuid"] = "00000000-0000-0000-0000-000000000001"
				if typ == "vless-reality" {
					raw["tls"] = true
					raw["servername"] = "example.test"
					raw["flow"] = "xtls-rprx-vision"
					raw["reality-opts"] = map[string]any{"public-key": base64.RawURLEncoding.EncodeToString(append([]byte{9}, make([]byte, 31)...)), "short-id": "ab12"}
				}
			} else if protocol == "vmess" {
				raw["uuid"] = "00000000-0000-0000-0000-000000000001"
				raw["alterId"] = 0
				raw["cipher"] = "auto"
				raw["udp"] = false
				if typ == "vmess-ws" {
					raw["network"] = "ws"
					raw["ws-opts"] = map[string]any{"path": "/protected"}
				}
			} else {
				raw["udp"] = true
			}
			if typ == "trojan" || typ == "ss" {
				raw["password"] = "local-test"
			}
			if typ == "ss" {
				raw["cipher"] = "aes-128-gcm"
			}
			if e := mobileProxyPolicy(raw, "proxy"); e != nil {
				t.Fatal(e)
			}
			proxy, e := adapter.ParseProxy(raw)
			if e != nil {
				t.Fatal(e)
			}
			defer proxy.Close()
			before := p.calls.Load()
			md := &C.Metadata{NetWork: C.TCP, Type: C.TUN, DstIP: netip.MustParseAddr("192.0.2.80"), DstPort: 443}
			if c, e := proxy.DialContext(m.ctx, md); e == nil {
				c.Close()
				t.Fatal("TCP escaped")
			}
			if p.calls.Load() == before {
				t.Fatal("TCP not protected")
			}
			if protocol != "vless" && proxy.SupportUDP() {
				before = p.calls.Load()
				md.NetWork = C.UDP
				if c, e := proxy.ListenPacketContext(m.ctx, md); e == nil {
					c.Close()
					t.Fatal("UDP escaped")
				}
				if p.calls.Load() == before {
					t.Fatal("UDP not protected")
				}
			}
		})
	}
}

// A real HTTP CONNECT peer answers DNS at an unroutable TEST-NET address.
// Successful resolution therefore proves GLOBAL was used, not a direct fallback.
func applicationDNSProxy(t *testing.T, m *mobileSession) *atomic.Int64 {
	t.Helper()
	hits := &atomic.Int64{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "CONNECT" || r.Host != "192.0.2.53:53" {
			t.Error("unexpected DNS proxy target", r.Method, r.Host)
			w.WriteHeader(400)
			return
		}
		hits.Add(1)
		c, rw, e := w.(http.Hijacker).Hijack()
		if e != nil {
			return
		}
		defer c.Close()
		_ = c.SetDeadline(time.Now().Add(3 * time.Second))
		_, _ = rw.WriteString("HTTP/1.1 200 Connection established\r\n\r\n")
		_ = rw.Flush()
		var size [2]byte
		if _, e = io.ReadFull(rw, size[:]); e != nil {
			return
		}
		b := make([]byte, int(binary.BigEndian.Uint16(size[:])))
		if _, e = io.ReadFull(rw, b); e != nil {
			return
		}
		q := new(D.Msg)
		if e = q.Unpack(b); e != nil {
			return
		}
		reply := new(D.Msg)
		reply.SetReply(q)
		for i := 0; i < 100; i++ {
			rr, _ := D.NewRR(q.Question[0].Name + " 30 IN A 192.0.2.25")
			reply.Answer = append(reply.Answer, rr)
		}
		b, _ = reply.Pack()
		binary.BigEndian.PutUint16(size[:], uint16(len(b)))
		_, _ = c.Write(append(size[:], b...))
	}))
	t.Cleanup(server.Close)
	ap := netip.MustParseAddrPort(strings.TrimPrefix(server.URL, "http://"))
	proxy, e := adapter.ParseProxy(map[string]any{"name": "dns-via-http", "type": "http", "server": ap.Addr().String(), "port": int(ap.Port())})
	if e != nil {
		t.Fatal(e)
	}
	m.cfg.Proxies["GLOBAL"] = proxy
	m.dns.servers = []string{"192.0.2.53:53"}
	return hits
}
func TestMobileApplicationDNSUsesGlobalAndTruncates(t *testing.T) {
	m, p := mobileFixture(t)
	hits := applicationDNSProxy(t, m)
	q := new(D.Msg)
	q.SetQuestion("app.test.", D.TypeA)
	b, _ := q.Pack()
	reply, e := m.dnsReply(b, true)
	if e != nil {
		t.Fatal(e)
	}
	r := new(D.Msg)
	if e = r.Unpack(reply); e != nil || r.Rcode != D.RcodeSuccess || !r.Truncated || len(reply) > 512 {
		t.Fatal(e, r, len(reply))
	}
	reply, e = m.dnsReply(b, false)
	if e != nil {
		t.Fatal(e)
	}
	_ = r.Unpack(reply)
	if r.Truncated || len(r.Answer) != 100 {
		t.Fatal("TCP truncated", r)
	}
	if hits.Load() != 2 || p.calls.Load() < 2 {
		t.Fatal("app DNS did not traverse protected GLOBAL")
	}
	p.accept.Store(false)
	reply, e = m.dnsReply(b, true)
	if e != nil {
		t.Fatal(e)
	}
	_ = r.Unpack(reply)
	if r.Rcode != D.RcodeServerFailure || hits.Load() != 2 {
		t.Fatal("failed proxy silently fell back")
	}
}

func TestMobileRejectsImplicitDirectAndBackgroundTransports(t *testing.T) {
	for _, change := range []struct{ from, to string }{
		{"name: GLOBAL", "name: Other"}, {", empty-fallback: REJECT", ""}, {"empty-fallback: REJECT", "empty-fallback: COMPATIBLE"},
	} {
		d, e := inspect(strings.Replace(mobileConfig, change.from, change.to, 1))
		if e == nil && mobilePolicy(d) == nil {
			t.Fatal("implicit direct admitted", change)
		}
	}
	for _, p := range []map[string]any{
		{"type": "vless", "udp": true}, {"type": "vless", "network": "grpc"}, {"type": "vless", "flow": "unknown-flow"},
		{"type": "vmess", "cipher": "auto", "udp": true}, {"type": "vmess", "cipher": "auto", "network": "grpc"},
		{"type": "vmess", "cipher": "auto", "network": "ws", "ws-opts": map[string]any{"max-early-data": 32}},
		{"type": "vmess", "cipher": "auto", "network": "ws", "ws-opts": map[string]any{"path": "/?ed=32"}},
		{"type": "vmess", "cipher": "auto", "network": "ws", "ws-opts": map[string]any{"v2ray-http-upgrade-fast-open": true}},
		{"type": "vmess", "cipher": "auto", "network": "tcp", "ws-opts": map[string]any{"path": "/ws"}},
		{"type": "vmess", "cipher": "none-but-not-supported"}, {"type": "vmess", "cipher": "auto", "grpc-opts": map[string]any{}},
		{"type": "vmess", "uuid": "00000000-0000-0000-0000-000000000001", "cipher": "auto"},
		{"type": "trojan", "network": "ws"}, {"type": "trojan", "reality-opts": map[string]any{}},
		{"type": "ss", "cipher": "2022-blake3-aes-128-gcm"}, {"type": "ss", "cipher": "aes-128-gcm", "udp-over-tcp": true},
	} {
		if e := mobileProxyPolicy(p, "proxy"); e == nil {
			t.Fatal("unaudited transport admitted", p)
		}
	}
}
func TestMobileReadinessRevokedAndTasksClose(t *testing.T) {
	m, _ := mobileFixture(t)
	singleton.mu.Lock()
	singleton.info = runtimeStatus{NetworkOwner: "android-vpn", TunReady: true}
	singleton.mu.Unlock()
	m.cancel()
	m.close()
	var r struct {
		Success bool
		Data    runtimeStatus
	}
	_ = json.Unmarshal([]byte(Invoke(`{"apiVersion":1,"operation":"status"}`)), &r)
	if !r.Success || r.Data.State != "failed" || r.Data.TunReady {
		t.Fatal("readiness survived cancellation", r)
	}
	if m.begin() {
		m.end()
		t.Fatal("late task admitted")
	}
	singleton.mu.Lock()
	singleton.info = runtimeStatus{}
	singleton.mu.Unlock()
}
