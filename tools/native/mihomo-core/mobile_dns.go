package mihomocore

import (
	"context"
	"errors"
	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/component/resolver"
	C "github.com/metacubex/mihomo/constant"
	D "github.com/miekg/dns"
	"net"
	"net/netip"
	"time"
)

// A session-scoped resolver with no system fallback, interface discovery,
// background cache refresh or detached singleflight. UDP and TCP truncation
// fallback both go through the same mandatory pre-connect protection hook.
type mobileResolver struct {
	session *mobileSession
	servers []string
}

func (r *mobileResolver) ExchangeContext(parent context.Context, q *D.Msg) (*D.Msg, error) {
	m := r.session
	if !m.begin() {
		return nil, net.ErrClosed
	}
	defer m.end()
	ctx, cancel := context.WithTimeout(parent, 5*time.Second)
	defer cancel()
	stop := context.AfterFunc(m.ctx, cancel)
	defer stop()
	var last error
	for _, server := range r.servers {
		for _, network := range []string{"udp4", "tcp4"} {
			nd := &net.Dialer{Control: dialer.DefaultSocketHook}
			conn, e := nd.DialContext(ctx, network, server)
			if e != nil {
				last = e
				break
			}
			if !m.track(conn) {
				return nil, net.ErrClosed
			}
			dc := &D.Conn{Conn: conn}
			client := &D.Client{Net: network, Timeout: 5 * time.Second}
			reply, _, e := client.ExchangeWithConnContext(ctx, q, dc)
			m.untrack(conn)
			if e != nil {
				last = e
				break
			}
			if reply.Truncated && network == "udp4" {
				continue
			}
			return reply, nil
		}
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
	}
	if last == nil {
		last = errors.New("no managed DNS upstream")
	}
	return nil, last
}

// App DNS follows GLOBAL with TCP, never direct UDP fallback. Bootstrap host
// resolution is explicitly direct/protected to avoid proxy-resolution recursion.
func (r *mobileResolver) ExchangeApplicationContext(parent context.Context, q *D.Msg) (*D.Msg, error) {
	m := r.session
	if !m.begin() {
		return nil, net.ErrClosed
	}
	defer m.end()
	ctx, cancel := context.WithTimeout(parent, 5*time.Second)
	defer cancel()
	stop := context.AfterFunc(m.ctx, cancel)
	defer stop()
	var last error
	for _, server := range r.servers {
		ap := netip.MustParseAddrPort(server)
		md := &C.Metadata{NetWork: C.TCP, Type: C.INNER, DstIP: ap.Addr(), DstPort: ap.Port()}
		m.graph.RLock()
		proxy, err := m.routeProxy(md)
		var conn net.Conn
		if err == nil {
			conn, err = proxy.DialContext(ctx, md)
		}
		m.graph.RUnlock()
		if err != nil {
			last = err
			continue
		}
		if !m.track(conn) {
			return nil, net.ErrClosed
		}
		client := &D.Client{Net: "tcp", Timeout: 5 * time.Second}
		reply, _, err := client.ExchangeWithConnContext(ctx, q, &D.Conn{Conn: conn})
		m.untrack(conn)
		if err == nil {
			return reply, nil
		}
		last = err
	}
	if last == nil {
		last = errors.New("no managed application DNS upstream")
	}
	return nil, last
}

func (r *mobileResolver) LookupIPv4(ctx context.Context, host string) ([]netip.Addr, error) {
	if ip, e := netip.ParseAddr(host); e == nil && ip.Is4() {
		return []netip.Addr{ip}, nil
	}
	q := new(D.Msg)
	q.SetQuestion(D.Fqdn(host), D.TypeA)
	reply, err := r.ExchangeContext(ctx, q)
	if err != nil {
		return nil, err
	}
	var ips []netip.Addr
	for _, rr := range reply.Answer {
		if a, ok := rr.(*D.A); ok {
			if ip, ok := netip.AddrFromSlice(a.A); ok {
				ips = append(ips, ip.Unmap())
			}
		}
	}
	if len(ips) == 0 {
		return nil, resolver.ErrIPNotFound
	}
	return ips, nil
}
func (r *mobileResolver) LookupIP(c context.Context, h string) ([]netip.Addr, error) {
	return r.LookupIPv4(c, h)
}
func (r *mobileResolver) LookupIPv6(context.Context, string) ([]netip.Addr, error) {
	return nil, resolver.ErrIPv6Disabled
}
func (r *mobileResolver) ResolveECH(context.Context, string) ([]byte, error) {
	return nil, errors.New("ECH unavailable in managed mobile DNS")
}
func (r *mobileResolver) Invalid() bool    { return true }
func (r *mobileResolver) ClearCache()      {}
func (r *mobileResolver) ResetConnection() {}

var _ resolver.Resolver = (*mobileResolver)(nil)
