//go:build with_gvisor

package mihomocore

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/gvisor/pkg/buffer"
	"github.com/metacubex/gvisor/pkg/tcpip"
	"github.com/metacubex/gvisor/pkg/tcpip/adapters/gonet"
	"github.com/metacubex/gvisor/pkg/tcpip/header"
	"github.com/metacubex/gvisor/pkg/tcpip/link/channel"
	"github.com/metacubex/gvisor/pkg/tcpip/stack"
	tun "github.com/metacubex/sing-tun"
	D "github.com/miekg/dns"
)

func packetRuntimeFixture(t *testing.T, yaml string) testReply {
	t.Helper()
	stopTest(t)
	dir := t.TempDir()
	t.Cleanup(func() { stopTest(t) })
	// Test-only hook. Fixtures use numeric localhost destinations, never OS TUN.
	protector := &mobileProtector{}
	protector.accept.Store(true)
	SetSocketProtector(protector)
	b, _ := json.Marshal(map[string]any{"apiVersion": 1, "requestId": "packet-fixture", "operation": "start", "yaml": yaml, "options": map[string]any{"dataDir": dir, "networkOwner": "ios-packet-flow", "packetIPv6": true}})
	var reply testReply
	if err := json.Unmarshal([]byte(invokeOwned(string(b), nil, true)), &reply); err != nil {
		t.Fatal(err)
	}
	requireOK(t, reply)
	var status runtimeStatus
	_ = json.Unmarshal(reply.Data, &status)
	if !status.TunReady || status.MixedAddress != "" || status.ControllerAddress != "" || status.NetworkOwner != "ios-packet-flow" {
		t.Fatalf("wrong owner: %+v", status)
	}
	return reply
}

const packetDNSConfig = "dns: {enable: true, default-nameserver: [127.0.0.1], nameserver: [127.0.0.1]}\n"

func TestPacketRuntimeTrustedEntryGenerationAndStopRead(t *testing.T) {
	stopTest(t)
	if call(t, "start", map[string]any{"yaml": simpleConfig, "options": map[string]any{"networkOwner": "ios-packet-flow", "dataDir": t.TempDir()}}).Success {
		t.Fatal("JSON forged packet owner")
	}
	r := packetRuntimeFixture(t, packetDNSConfig+simpleConfig)
	if WriteIOSPacket(r.Generation+1, flowPacket(4)) != -1 {
		t.Fatal("stale input accepted")
	}
	if WriteIOSPacket(r.Generation, []byte{4}) != -2 {
		t.Fatal("invalid input accepted")
	}
	if _, code := ReadIOSPacket(r.Generation, 1); code != 0 {
		t.Fatal("empty output did not time out", code)
	}
	done := make(chan int, 1)
	go func() { _, code := ReadIOSPacket(r.Generation, 1000); done <- code }()
	requireOK(t, call(t, "stop", map[string]any{"generation": r.Generation}))
	select {
	case code := <-done:
		if code != -1 {
			t.Fatal("stopped read returned packet", code)
		}
	case <-time.After(time.Second):
		t.Fatal("stop blocked behind output read")
	}
	if WriteIOSPacket(r.Generation, flowPacket(4)) != -1 {
		t.Fatal("stopped input accepted")
	}
}

func TestPacketRuntimeNativeUDPResolver(t *testing.T) {
	listener, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	server := &D.Server{PacketConn: listener, Handler: D.HandlerFunc(func(w D.ResponseWriter, q *D.Msg) {
		r := new(D.Msg)
		r.SetReply(q)
		r.Answer = []D.RR{&D.A{Hdr: D.RR_Header{Name: q.Question[0].Name, Rrtype: D.TypeA, Class: D.ClassINET, Ttl: 60}, A: net.IPv4(192, 0, 2, 20)}}
		_ = w.WriteMsg(r)
	})}
	go func() { _ = server.ActivateAndServe() }()
	t.Cleanup(func() { _ = server.Shutdown(); _ = listener.Close() })
	r := packetRuntimeFixture(t, fmt.Sprintf("dns: {enable: true, default-nameserver: [127.0.0.1], nameserver: ['%s']}\n", listener.LocalAddr())+simpleConfig)
	q := new(D.Msg)
	q.SetQuestion("native-packet.test.", D.TypeA)
	dns, _ := q.Pack()
	raw := make([]byte, 28+len(dns))
	copy(raw[28:], dns)
	ip := header.IPv4(raw)
	ip.Encode(&header.IPv4Fields{TotalLength: uint16(len(raw)), TTL: 64, Protocol: 17, SrcAddr: tcpip.AddrFrom4([4]byte{172, 19, 0, 1}), DstAddr: tcpip.AddrFrom4([4]byte{172, 19, 0, 2})})
	ip.SetChecksum(^ip.CalculateChecksum())
	header.UDP(raw[20:]).Encode(&header.UDPFields{SrcPort: 25000, DstPort: 53, Length: uint16(len(raw) - 20)})
	if WriteIOSPacket(r.Generation, raw) != 1 {
		t.Fatal("DNS packet rejected")
	}
	output, code := ReadIOSPacket(r.Generation, 1000)
	if code <= 0 {
		t.Fatal("native DNS did not answer", code)
	}
	h := header.IPv4(output)
	u := header.UDP(output[h.HeaderLength():])
	answer := new(D.Msg)
	if err := answer.Unpack(u.Payload()); err != nil || answer.Id != q.Id || len(answer.Answer) != 1 || u.SourcePort() != 53 {
		t.Fatal("wrong native DNS answer", err, answer)
	}
}

func packetClient(t *testing.T, generation uint64) *stack.Stack {
	t.Helper()
	peer := channel.New(128, 1500, "")
	client, err := tun.NewGVisorStack(peer)
	if err != nil {
		t.Fatal(err)
	}
	if err := client.AddProtocolAddress(1, tcpip.ProtocolAddress{Protocol: header.IPv4ProtocolNumber, AddressWithPrefix: tcpip.AddressWithPrefix{Address: tcpip.AddrFrom4([4]byte{172, 19, 0, 1}), PrefixLen: 30}}, stack.AddressProperties{}); err != nil {
		t.Fatal(err)
	}
	if err := client.AddProtocolAddress(1, tcpip.ProtocolAddress{Protocol: header.IPv6ProtocolNumber,
		AddressWithPrefix: tcpip.AddressWithPrefix{Address: tcpip.AddrFrom16([16]byte{0xfd, 0xfe, 0xdc, 0xba, 0x98, 0x76, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), PrefixLen: 126}}, stack.AddressProperties{}); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{}, 2)
	go func() {
		defer func() { done <- struct{}{} }()
		for {
			p := peer.ReadContext(ctx)
			if p == nil {
				return
			}
			v := p.ToView()
			code := WriteIOSPacket(generation, v.AsSlice())
			v.Release()
			p.DecRef()
			if code != 1 {
				return
			}
		}
	}()
	go func() {
		defer func() { done <- struct{}{} }()
		for ctx.Err() == nil {
			raw, n := ReadIOSPacket(generation, 100)
			if n == 0 {
				continue
			}
			if n < 0 {
				return
			}
			protocol, e := packetFlowProtocol(raw, 1500)
			if e != nil {
				return
			}
			p := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(raw)})
			peer.InjectInbound(protocol, p)
			p.DecRef()
		}
	}()
	t.Cleanup(func() {
		cancel()
		client.Close()
		for _, ep := range client.CleanupEndpoints() {
			ep.Abort()
		}
		peer.Close()
		<-done
		<-done
	})
	return client
}

func TestPacketRuntimeNativeTCPAndLiveSelection(t *testing.T) {
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { listener.Close() })
	go func() {
		for {
			c, err := listener.Accept()
			if err != nil {
				return
			}
			go func() {
				defer c.Close()
				_ = c.SetDeadline(time.Now().Add(5 * time.Second))
				var hello [2]byte
				if _, err := io.ReadFull(c, hello[:]); err != nil {
					return
				}
				methods := make([]byte, int(hello[1]))
				if _, err := io.ReadFull(c, methods); err != nil {
					return
				}
				if _, err := c.Write([]byte{5, 0}); err != nil {
					return
				}
				var request [4]byte
				if _, err := io.ReadFull(c, request[:]); err != nil || request[1] != 1 {
					return
				}
				size := 6
				if request[3] == 4 {
					size = 18
				} else if request[3] != 1 {
					return
				}
				if _, err := io.ReadFull(c, make([]byte, size)); err != nil {
					return
				}
				if _, err := c.Write([]byte{5, 0, 0, 1, 127, 0, 0, 1, 0, 1}); err != nil {
					return
				}
				_, _ = io.Copy(c, c)
			}()
		}
	}()
	target := listener.Addr().(*net.TCPAddr)
	source := strings.Replace(simpleConfig, "{name: local, type: direct}", fmt.Sprintf("{name: local, type: socks5, server: 127.0.0.1, port: %d}", target.Port), 1)
	r := packetRuntimeFixture(t, packetDNSConfig+source)
	client := packetClient(t, r.Generation)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	connection, err := gonet.DialContextTCP(ctx, client, tcpip.FullAddress{NIC: 1, Addr: tcpip.AddrFrom4([4]byte{203, 0, 113, 10}), Port: 443}, header.IPv4ProtocolNumber)
	if err != nil {
		t.Fatal(err)
	}
	defer connection.Close()
	connection6, err := gonet.DialContextTCP(ctx, client, tcpip.FullAddress{NIC: 1,
		Addr: tcpip.AddrFrom16([16]byte{0x20, 1, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), Port: 443}, header.IPv6ProtocolNumber)
	if err != nil {
		t.Fatal(err)
	}
	defer connection6.Close()
	selectionExchange(t, connection6, connection6)
	selectionExchange(t, connection, connection)
	requireOK(t, call(t, "select", map[string]any{"group": "Choice", "name": "local"}))
	selectionExchange(t, connection, connection)
	requireOK(t, call(t, "select", map[string]any{"group": "Choice", "name": "REJECT"}))
	selectionDisconnected(t, connection, connection)
	selectionDisconnected(t, connection6, connection6)
	_ = connection.SetReadDeadline(time.Now().Add(time.Second))
	if _, err := io.ReadFull(connection, make([]byte, 1)); err == nil {
		t.Fatal("closed route still readable")
	}
}

func TestPacketRuntimeRejectsUnownedProcessRules(t *testing.T) {
	for _, source := range []string{"rules: ['PROCESS-NAME,example,REJECT']", "sub-rules: {a: ['UID,100,REJECT']}"} {
		d, err := inspect(packetDNSConfig + source)
		if err != nil {
			t.Fatal(err)
		}
		if packetRuntimePolicy(d) == nil {
			t.Fatal("process/UID ownership silently discarded")
		}
	}
}

// Cancellation is real network cancellation, not just dismissal of the spinner.
func TestPacketRuntimeDelayCancellation(t *testing.T) {
	r := packetRuntimeFixture(t, packetDNSConfig+simpleConfig)
	entered := make(chan struct{}, 1)
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		entered <- struct{}{}
		<-req.Context().Done()
	}))
	defer target.Close()
	done := make(chan testReply, 1)
	go func() {
		done <- call(t, "delay", map[string]any{"generation": r.Generation, "name": "local", "url": target.URL, "timeoutMs": 10000, "expectedStatus": "200"})
	}()
	select {
	case <-entered:
	case <-time.After(2 * time.Second):
		t.Fatal("probe never reached local target")
	}
	requireOK(t, call(t, "cancel", map[string]any{"generation": r.Generation, "targetRequestId": "test-delay"}))
	select {
	case reply := <-done:
		if reply.Success {
			t.Fatal("cancelled probe succeeded")
		}
	case <-time.After(time.Second):
		t.Fatal("cancel did not interrupt native URLTest")
	}
	singleton.mu.Lock()
	leaked := singleton.probeCancel != nil || singleton.probeRequestID != ""
	singleton.mu.Unlock()
	if leaked {
		t.Fatal("probe cancellation owner leaked")
	}
}

func TestPacketRuntimeDelayCancelledBeforeDispatch(t *testing.T) {
	r := packetRuntimeFixture(t, packetDNSConfig+simpleConfig)
	hits := 0
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) { hits++; w.WriteHeader(204) }))
	defer target.Close()
	requireOK(t, call(t, "cancel", map[string]any{"generation": r.Generation, "targetRequestId": "test-nimboDelay"}))
	reply := call(t, "nimboDelay", map[string]any{"generation": r.Generation, "name": "local", "url": target.URL, "timeoutMs": 1000, "expectedStatus": "204"})
	if reply.Success || reply.Error == nil || reply.Error.Code != "PROBE_CANCELLED" {
		t.Fatalf("early cancellation not respected: %+v", reply)
	}
	if hits != 0 {
		t.Fatal("cancelled probe performed network I/O")
	}
}
