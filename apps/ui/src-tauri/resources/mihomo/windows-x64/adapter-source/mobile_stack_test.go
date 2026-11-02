//go:build with_gvisor

package mihomocore

import (
	"context"
	"encoding/binary"
	"io"
	"net/netip"
	"testing"
	"time"

	"github.com/metacubex/gvisor/pkg/buffer"
	"github.com/metacubex/gvisor/pkg/tcpip"
	"github.com/metacubex/gvisor/pkg/tcpip/adapters/gonet"
	"github.com/metacubex/gvisor/pkg/tcpip/header"
	"github.com/metacubex/gvisor/pkg/tcpip/link/channel"
	"github.com/metacubex/gvisor/pkg/tcpip/stack"
	"github.com/metacubex/mihomo/log"
	tun "github.com/metacubex/sing-tun"
	D "github.com/miekg/dns"
)

func testMobileStack(t *testing.T, m *mobileSession) *PacketFlowTun {
	t.Helper()
	device, e := NewPacketFlowTun(1500)
	if e != nil {
		t.Fatal(e)
	}
	s, e := tun.NewStack("gvisor", tun.StackOptions{Context: m.ctx, Tun: device, TunOptions: tun.Options{MTU: 1500, Inet4Address: []netip.Prefix{netip.MustParsePrefix("172.19.0.1/30")}}, Handler: m, Logger: log.SingLogger})
	if e != nil {
		device.Close()
		t.Fatal(e)
	}
	if e = s.Start(); e != nil {
		device.Close()
		s.Close()
		t.Fatal(e)
	}
	t.Cleanup(func() { m.cancel(); device.Close(); s.Close(); m.close() })
	return device
}
func TestMobileRealGvisorUDPManagedDNS(t *testing.T) {
	m, _ := mobileFixture(t)
	hits := applicationDNSProxy(t, m)
	device := testMobileStack(t, m)
	q := new(D.Msg)
	q.SetQuestion("packet.test.", D.TypeA)
	dns, _ := q.Pack()
	raw := make([]byte, 28+len(dns))
	copy(raw[28:], dns)
	ip := header.IPv4(raw)
	ip.Encode(&header.IPv4Fields{TotalLength: uint16(len(raw)), TTL: 64, Protocol: 17, SrcAddr: tcpip.AddrFrom4([4]byte{172, 19, 0, 1}), DstAddr: tcpip.AddrFrom4([4]byte{172, 19, 0, 2})})
	ip.SetChecksum(^ip.CalculateChecksum())
	udp := header.UDP(raw[20:])
	udp.Encode(&header.UDPFields{SrcPort: 25000, DstPort: 53, Length: uint16(len(raw) - 20)})
	if err := device.IngestPacket(raw); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(m.ctx, 3*time.Second)
	defer cancel()
	b, e := device.ReadPacket(ctx)
	if e != nil {
		t.Fatal("real packet flow did not return DNS", e)
	}
	h := header.IPv4(b)
	u := header.UDP(b[h.HeaderLength():])
	r := new(D.Msg)
	if e := r.Unpack(u.Payload()); e != nil || r.Id != q.Id || r.Rcode != D.RcodeSuccess || !r.Truncated || u.SourcePort() != 53 || u.DestinationPort() != 25000 || hits.Load() != 1 {
		t.Fatal("invalid real stack DNS response", e, r)
	}
}
func TestMobileRealGvisorTCPManagedDNS(t *testing.T) {
	m, _ := mobileFixture(t)
	hits := applicationDNSProxy(t, m)
	device := testMobileStack(t, m)
	peer := channel.New(128, 1500, "")
	client, e := tun.NewGVisorStack(peer)
	if e != nil {
		t.Fatal(e)
	}
	address := tcpip.AddrFrom4([4]byte{172, 19, 0, 1})
	if err := client.AddProtocolAddress(1, tcpip.ProtocolAddress{Protocol: header.IPv4ProtocolNumber, AddressWithPrefix: tcpip.AddressWithPrefix{Address: address, PrefixLen: 30}}, stack.AddressProperties{}); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(m.ctx)
	done := make(chan struct{}, 2)
	go func() {
		defer func() { done <- struct{}{} }()
		for {
			p := peer.ReadContext(ctx)
			if p == nil {
				return
			}
			v := p.ToView()
			err := device.IngestPacket(v.AsSlice())
			v.Release()
			p.DecRef()
			if err != nil {
				return
			}
		}
	}()
	go func() {
		defer func() { done <- struct{}{} }()
		for {
			raw, err := device.ReadPacket(ctx)
			if err != nil {
				return
			}
			protocol, err := packetFlowProtocol(raw, 1500)
			if err != nil {
				return
			}
			p := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(raw)})
			peer.InjectInbound(protocol, p)
			p.DecRef()
		}
	}()
	defer func() {
		cancel()
		client.Close()
		for _, ep := range client.CleanupEndpoints() {
			ep.Abort()
		}
		peer.Close()
		<-done
		<-done
	}()
	dialctx, dc := context.WithTimeout(ctx, 3*time.Second)
	defer dc()
	conn, e := gonet.DialContextTCP(dialctx, client, tcpip.FullAddress{NIC: 1, Addr: tcpip.AddrFrom4([4]byte{172, 19, 0, 2}), Port: 53}, header.IPv4ProtocolNumber)
	if e != nil {
		t.Fatal(e)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(3 * time.Second))
	q := new(D.Msg)
	q.SetQuestion("tcp-packet.test.", D.TypeA)
	b, _ := q.Pack()
	raw := make([]byte, 2+len(b))
	binary.BigEndian.PutUint16(raw, uint16(len(b)))
	copy(raw[2:], b)
	if _, e = conn.Write(raw); e != nil {
		t.Fatal(e)
	}
	var size [2]byte
	if _, e = io.ReadFull(conn, size[:]); e != nil {
		t.Fatal(e)
	}
	b = make([]byte, binary.BigEndian.Uint16(size[:]))
	if _, e = io.ReadFull(conn, b); e != nil {
		t.Fatal(e)
	}
	r := new(D.Msg)
	if e = r.Unpack(b); e != nil || len(r.Answer) != 100 || hits.Load() != 1 {
		t.Fatal(e, r)
	}
}
