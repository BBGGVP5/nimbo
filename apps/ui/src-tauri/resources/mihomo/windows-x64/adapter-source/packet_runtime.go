//go:build with_gvisor

package mihomocore

import (
	"net/netip"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/inbound"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/listener/sing"
	singTun "github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	tun "github.com/metacubex/sing-tun"
)

const packetFlowCompiled = true

type nativePacketFlow struct {
	*PacketFlowTun
	stack tun.Stack
	once  sync.Once
}

func (p *nativePacketFlow) Close() error {
	p.once.Do(func() { _ = p.PacketFlowTun.Close(); _ = p.stack.Close() })
	return nil
}

// Public packet flow owns IP frames only. The upstream listener handler owns
// protocol dispatch, full rules/subrules, sniffer, DNS and native group routing.
// No OS interface, routing table, public listener or second core is opened.
func startPacketRuntime(s *session) (ownedPacketFlow, error) {
	device, err := NewPacketFlowTun(1500)
	if err != nil {
		return nil, err
	}
	h, err := sing.NewListenerHandler(sing.ListenerConfig{Tunnel: tunnel.Tunnel, Type: C.TUN, Additions: []inbound.Addition{inbound.WithInName("NIMBO-PACKET-FLOW")}})
	if err != nil {
		device.Close()
		return nil, err
	}
	inet4 := []netip.Prefix{netip.MustParsePrefix("172.19.0.1/30")}
	var inet6 []netip.Prefix
	dns := []netip.AddrPort{netip.MustParseAddrPort("172.19.0.2:53")}
	if s.cfg.General.IPv6 {
		inet6 = []netip.Prefix{netip.MustParsePrefix("fdfe:dcba:9876::1/126")}
		dns = append(dns, netip.MustParseAddrPort("[fdfe:dcba:9876::2]:53"))
	}
	projection := androidTunProjection(s.cfg.General.Tun, 0, s.cfg.General.IPv6)
	for _, value := range projection.DNSHijack {
		if value == "any:53" {
			value = "0.0.0.0:53"
		}
		addr, e := netip.ParseAddrPort(value)
		if e != nil {
			device.Close()
			return nil, problem("INVALID_CONFIG", "tun.dns-hijack", "IP:port required")
		}
		dns = append(dns, addr)
	}
	handler := &singTun.ListenerHandler{ListenerHandler: h, DnsAddrPorts: dns, Inet4Address: inet4, Inet6Address: inet6, DisableICMPForwarding: projection.DisableICMPForwarding}
	stack, err := tun.NewStack("gvisor", tun.StackOptions{Context: s.ctx, Tun: device, TunOptions: tun.Options{MTU: 1500, Inet4Address: inet4, Inet6Address: inet6}, Handler: handler, Logger: log.SingLogger, UDPTimeout: sing.UDPTimeout, ICMPTimeout: 10 * time.Second})
	if err != nil {
		device.Close()
		return nil, err
	}
	p := &nativePacketFlow{PacketFlowTun: device, stack: stack}
	if err = stack.Start(); err != nil {
		p.Close()
		return nil, err
	}
	return p, nil
}
