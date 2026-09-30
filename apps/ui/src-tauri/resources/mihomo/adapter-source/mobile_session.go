package mihomocore

import (
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"net/netip"
	"sync"
	"time"

	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel"
	tun "github.com/metacubex/sing-tun"
	"github.com/metacubex/sing/common/buf"
	M "github.com/metacubex/sing/common/metadata"
	N "github.com/metacubex/sing/common/network"
	D "github.com/miekg/dns"
)

const mobileMaxFlows = 256
const mobileQueueSize = 32

// Every mobile task and socket belongs to one session. In particular no packet
// enters tunnel.HandleUDPPacket's process-global queue/context.Background dial.
type mobileSession struct {
	ctx          context.Context
	cancel       context.CancelFunc
	cfg          *config.Config
	graph        sync.RWMutex
	mu           sync.Mutex
	closed       bool
	tasks        int
	sockets      map[io.Closer]struct{}
	flows        map[string]*mobileFlow
	wg           sync.WaitGroup
	shutdownDone chan struct{}
	dns          *mobileResolver
}
type mobilePacket struct {
	b      *buf.Buffer
	writer N.PacketWriter
	dest   M.Socksaddr
}
type mobileFlow struct {
	queue chan mobilePacket
	done  chan struct{}
}

func newMobileSession(ctx context.Context, cancel context.CancelFunc) *mobileSession {
	m := &mobileSession{ctx: ctx, cancel: cancel, sockets: map[io.Closer]struct{}{}, flows: map[string]*mobileFlow{}, shutdownDone: make(chan struct{})}
	go func() {
		<-ctx.Done()
		m.mu.Lock()
		m.closed = true
		cs := make([]io.Closer, 0, len(m.sockets))
		for c := range m.sockets {
			cs = append(cs, c)
		}
		m.mu.Unlock()
		for _, c := range cs {
			_ = c.Close()
		}
		close(m.shutdownDone)
	}()
	return m
}
func (m *mobileSession) begin() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.closed || m.ctx.Err() != nil || m.tasks >= 512 {
		return false
	}
	m.wg.Add(1)
	m.tasks++
	return true
}
func (m *mobileSession) end() {
	m.mu.Lock()
	m.tasks--
	m.mu.Unlock()
	m.wg.Done()
}
func (m *mobileSession) track(c io.Closer) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.closed || m.ctx.Err() != nil {
		_ = c.Close()
		return false
	}
	m.sockets[c] = struct{}{}
	return true
}
func (m *mobileSession) untrack(c io.Closer) {
	_ = c.Close()
	m.mu.Lock()
	delete(m.sockets, c)
	m.mu.Unlock()
}
func (m *mobileSession) close() { m.cancel(); <-m.shutdownDone; m.wg.Wait() }
func (m *mobileSession) installDNS(d *inspection) {
	cfg := d.root["dns"].(map[string]any)
	var servers []string
	for _, v := range cfg["nameserver"].([]any) {
		a, _ := mobileDNSAddress(v.(string))
		servers = append(servers, a)
	}
	m.dns = &mobileResolver{session: m, servers: servers}
	resolver.DefaultHosts = resolver.NewHosts(m.cfg.Hosts)
	resolver.UseSystemHosts = false
	resolver.DefaultResolver = m.dns
	resolver.ProxyServerHostResolver = m.dns
	resolver.DirectHostResolver = m.dns
}
func mobileMetadata(md M.Metadata, network C.NetWork) *C.Metadata {
	return &C.Metadata{NetWork: network, Type: C.TUN, SrcIP: md.Source.Addr, SrcPort: md.Source.Port, DstIP: md.Destination.Addr, DstPort: md.Destination.Port}
}
func mobileDestination(md M.Metadata) bool {
	return md.Source.IsIPv4() && md.Destination.IsIPv4() && md.Destination.Port != 0
}

// routeProxy dispatches from this session's parsed native graph. It deliberately
// does not call tunnel.resolveMetadata/match: those helpers use process-global
// queues and context.Background. Unsupported/unmatched routes are dropped.
func (m *mobileSession) routeProxy(md *C.Metadata) (C.Proxy, error) {
	if m.cfg == nil || md == nil || md.DstPort == 0 || !md.DstIP.IsValid() {
		return nil, errors.New("invalid destination")
	}
	switch m.cfg.General.Mode {
	case tunnel.Global:
		proxy := m.cfg.Proxies["GLOBAL"]
		if proxy == nil {
			return nil, errors.New("GLOBAL proxy group is required")
		}
		if md.NetWork == C.UDP && !proxy.SupportUDP() {
			return nil, errors.New("GLOBAL proxy does not support UDP; flow dropped")
		}
		return proxy, nil
	case tunnel.Rule:
		for _, rule := range m.cfg.Rules {
			matched, target := rule.Match(md, C.RuleMatchHelper{})
			if !matched {
				continue
			}
			proxy, ok := m.cfg.Proxies[target]
			if !ok || proxy == nil {
				return nil, fmt.Errorf("matched rule target %q is unavailable; flow dropped", target)
			}
			if md.NetWork == C.UDP && !proxy.SupportUDP() {
				return nil, fmt.Errorf("matched rule target %q does not support UDP; flow dropped", target)
			}
			return proxy, nil
		}
		return nil, errors.New("no mobile rule matched; flow dropped")
	default:
		return nil, errors.New("unsupported mobile mode; flow dropped")
	}
}
func (m *mobileSession) PrepareConnection(string, M.Socksaddr, M.Socksaddr, tun.DirectRouteContext, time.Duration) (tun.DirectRouteDestination, error) {
	return nil, tun.ErrDrop
}
func (m *mobileSession) NewError(context.Context, error) {} // no sensitive source logging

func (m *mobileSession) NewConnection(_ context.Context, conn net.Conn, md M.Metadata) error {
	if !mobileDestination(md) || !m.begin() {
		_ = conn.Close()
		return net.ErrClosed
	}
	defer m.end()
	if !m.track(conn) {
		return net.ErrClosed
	}
	defer m.untrack(conn)
	if md.Destination.Addr == netip.MustParseAddr("172.19.0.2") && md.Destination.Port == 53 {
		return m.dnsTCP(conn)
	}
	ctx, cancel := context.WithTimeout(m.ctx, 5*time.Second)
	defer cancel()
	m.graph.RLock()
	proxy, err := m.routeProxy(mobileMetadata(md, C.TCP))
	if err == nil {
		remote, dialErr := proxy.DialContext(ctx, mobileMetadata(md, C.TCP))
		if dialErr == nil {
			if !m.track(remote) {
				m.graph.RUnlock()
				return net.ErrClosed
			}
			defer m.untrack(remote)
			done := make(chan struct{})
			go func() { _, _ = io.Copy(remote, conn); _ = remote.Close(); _ = conn.Close(); close(done) }()
			_, _ = io.Copy(conn, remote)
			_ = conn.Close()
			_ = remote.Close()
			<-done
			m.graph.RUnlock()
			return nil
		}
		err = dialErr
	}
	m.graph.RUnlock()
	return err
}
func (m *mobileSession) dnsTCP(conn net.Conn) error {
	for {
		_ = conn.SetReadDeadline(time.Now().Add(30 * time.Second))
		var size [2]byte
		if _, e := io.ReadFull(conn, size[:]); e != nil {
			return e
		}
		n := int(binary.BigEndian.Uint16(size[:]))
		if n == 0 {
			return errors.New("empty DNS message")
		}
		b := make([]byte, n)
		if _, e := io.ReadFull(conn, b); e != nil {
			return e
		}
		reply, e := m.dnsReply(b, false)
		if e != nil {
			return e
		}
		binary.BigEndian.PutUint16(size[:], uint16(len(reply)))
		_ = conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
		if _, e = io.Copy(conn, bytes.NewReader(append(size[:], reply...))); e != nil {
			return e
		}
	}
}
func (m *mobileSession) dnsReply(b []byte, udp bool) ([]byte, error) {
	q := new(D.Msg)
	if err := q.Unpack(b); err != nil {
		return nil, err
	}
	// IPv6 is blocked by the platform plan and must not be advertised by DNS.
	for _, question := range q.Question {
		if question.Qtype == D.TypeAAAA {
			r := new(D.Msg)
			r.SetReply(q)
			return r.Pack()
		}
	}
	r, err := m.dns.ExchangeApplicationContext(m.ctx, q)
	if err != nil {
		r = new(D.Msg)
		r.SetRcode(q, D.RcodeServerFailure)
	}
	if udp {
		limit := 512
		if opt := q.IsEdns0(); opt != nil {
			limit = int(opt.UDPSize())
			if limit < 512 {
				limit = 512
			}
			if limit > 1232 {
				limit = 1232
			}
		}
		r.Truncate(limit)
	}
	return r.Pack()
}
func (m *mobileSession) NewPacket(_ context.Context, key netip.AddrPort, b *buf.Buffer, md M.Metadata, init func(N.PacketConn) N.PacketWriter) {
	if !mobileDestination(md) {
		b.Release()
		return
	}
	if md.Destination.Addr == netip.MustParseAddr("172.19.0.2") && md.Destination.Port == 53 {
		if !m.begin() {
			b.Release()
			return
		}
		writer := init(nil)
		go func() {
			defer m.end()
			defer b.Release()
			reply, err := m.dnsReply(b.Bytes(), true)
			if err != nil || m.ctx.Err() != nil {
				return
			}
			out := buf.As(reply)
			_ = writer.WritePacket(out, md.Destination)
		}()
		return
	}
	// Endpoint-dependent NAT is explicit in the managed plan; each flow retains
	// its selected native proxy until idle expiry/stop. Never share across sessions.
	flowKey := key.String() + "|" + md.Destination.String()
	m.mu.Lock()
	if m.closed || m.ctx.Err() != nil {
		m.mu.Unlock()
		b.Release()
		return
	}
	f := m.flows[flowKey]
	if f == nil {
		if len(m.flows) >= mobileMaxFlows {
			m.mu.Unlock()
			b.Release()
			return
		}
		f = &mobileFlow{queue: make(chan mobilePacket, mobileQueueSize), done: make(chan struct{})}
		m.flows[flowKey] = f
		m.wg.Add(1)
		go m.runFlow(flowKey, f, md)
	}
	p := mobilePacket{b: b, writer: init(nil), dest: md.Destination}
	select {
	case f.queue <- p:
	default:
		b.Release()
	}
	m.mu.Unlock()
}
func (m *mobileSession) runFlow(key string, f *mobileFlow, md M.Metadata) {
	defer m.wg.Done()
	defer func() {
		m.mu.Lock()
		delete(m.flows, key)
		close(f.done)
		for {
			select {
			case p := <-f.queue:
				p.b.Release()
			default:
				m.mu.Unlock()
				return
			}
		}
	}()
	ctx, cancel := context.WithTimeout(m.ctx, 5*time.Second)
	m.graph.RLock()
	proxy, routeErr := m.routeProxy(mobileMetadata(md, C.UDP))
	if routeErr != nil {
		m.graph.RUnlock()
		cancel()
		return
	}
	if !proxy.SupportUDP() {
		m.graph.RUnlock()
		cancel()
		return
	}
	pc, err := proxy.ListenPacketContext(ctx, mobileMetadata(md, C.UDP))
	m.graph.RUnlock()
	cancel()
	if err != nil {
		return
	}
	if !m.track(pc) {
		return
	}
	defer m.untrack(pc)
	readDone := make(chan struct{})
	var writerMu sync.RWMutex
	var writer N.PacketWriter
	go func() {
		defer close(readDone)
		for {
			_ = pc.SetReadDeadline(time.Now().Add(60 * time.Second))
			data, put, addr, e := pc.WaitReadFrom()
			if e != nil {
				_ = pc.Close()
				return
			}
			writerMu.RLock()
			w := writer
			writerMu.RUnlock()
			if w != nil && m.ctx.Err() == nil {
				out := buf.As(append([]byte(nil), data...))
				_ = w.WritePacket(out, M.SocksaddrFromNet(addr))
			}
			if put != nil {
				put()
			}
		}
	}()
	defer func() { _ = pc.Close(); <-readDone }()
	for {
		select {
		case <-m.ctx.Done():
			return
		case <-readDone:
			return
		case p := <-f.queue:
			if m.ctx.Err() != nil {
				p.b.Release()
				return
			}
			writerMu.Lock()
			writer = p.writer
			writerMu.Unlock()
			_ = pc.SetWriteDeadline(time.Now().Add(5 * time.Second))
			_, err = pc.WriteTo(p.b.Bytes(), p.dest.UDPAddr())
			p.b.Release()
			if err != nil {
				return
			}
		}
	}
}

var _ tun.Handler = (*mobileSession)(nil)
