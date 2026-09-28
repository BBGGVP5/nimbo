package awgcore

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"net/netip"
	"strings"
	"testing"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun/netstack"
	"golang.org/x/crypto/curve25519"
)

func pair(t *testing.T) ([]byte, []byte) {
	t.Helper()
	p := make([]byte, 32)
	if _, e := rand.Read(p); e != nil {
		t.Fatal(e)
	}
	pub, e := curve25519.X25519(p, curve25519.Basepoint)
	if e != nil {
		t.Fatal(e)
	}
	return p, pub
}
func testPeer(t *testing.T, params string) (*Runtime, *netstack.Net) {
	t.Helper()
	client, clientPub := pair(t)
	server, serverPub := pair(t)
	// A loopback-only encrypted peer; no physical VPN or Internet endpoints.
	socket, e := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if e != nil {
		t.Fatal(e)
	}
	port := socket.LocalAddr().(*net.UDPAddr).Port
	socket.Close()
	tun, stack, e := netstack.CreateNetTUN([]netip.Addr{netip.MustParseAddr("10.23.0.2")}, []netip.Addr{}, 1280)
	if e != nil {
		t.Fatal(e)
	}
	dev := device.NewDevice(tun, conn.NewDefaultBind(), device.NewLogger(device.LogLevelSilent, ""))
	t.Cleanup(dev.Close)

	uapi := fmt.Sprintf("private_key=%s\nlisten_port=%d\n%sreplace_peers=true\npublic_key=%s\nallowed_ip=10.23.0.1/32\n", hex.EncodeToString(server), port, params, hex.EncodeToString(clientPub))
	if e = dev.IpcSet(uapi); e != nil {
		t.Fatal(e)
	}
	if e = dev.Up(); e != nil {
		t.Fatal(e)
	}
	cfg := fmt.Sprintf("[Interface]\nPrivateKey=%s\nAddress=10.23.0.1/32\n%s[Peer]\nPublicKey=%s\nEndpoint=127.0.0.1:%d\nAllowedIPs=10.23.0.2/32\n", base64.StdEncoding.EncodeToString(client), params, base64.StdEncoding.EncodeToString(serverPub), port)
	r, e := Start(cfg, "127.0.0.1:0", "nimbo", "0123456789abcdef0123456789abcdef")
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(r.Close)
	return r, stack
}

func socksClient(t *testing.T, r *Runtime) net.Conn {
	t.Helper()
	c, e := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", r.Port()), time.Second)
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(func() { c.Close() })
	_ = c.SetDeadline(time.Now().Add(10 * time.Second))
	_, _ = c.Write([]byte{5, 1, 2})
	b := make([]byte, 2)
	if _, e = io.ReadFull(c, b); e != nil || b[1] != 2 {
		t.Fatal("method", e)
	}
	auth := append([]byte{1, 5}, []byte("nimbo")...)
	auth = append(auth, 32)
	auth = append(auth, []byte("0123456789abcdef0123456789abcdef")...)
	_, _ = c.Write(auth)
	if _, e = io.ReadFull(c, b); e != nil || b[1] != 0 {
		t.Fatal("auth", e)
	}
	return c
}
func request(t *testing.T, c net.Conn, command byte, address string) string {
	t.Helper()
	b, _ := addressBytes(address)
	_, _ = c.Write(append([]byte{5, command, 0}, b...))
	header := make([]byte, 3)
	if _, e := io.ReadFull(c, header); e != nil || header[1] != 0 {
		t.Fatalf("request failed %v %v", header, e)
	}
	addr, e := readAddress(c)
	if e != nil {
		t.Fatal(e)
	}
	return addr
}
func TestEncryptedTCPAndUDP(t *testing.T) {
	for name, params := range map[string]string{
		"wireguard-compatible":    "",
		"awg-obfuscation":         "jc=2\njmin=40\njmax=70\ns1=10\ns2=20\ns3=5\ns4=7\nh1=111111\nh2=222222\nh3=333333\nh4=444444\n",
		"awg31-header-protection": "jc=2\njmin=40\njmax=70\ns1=16\ns2=20\ns3=24\ns4=28\nh1=111111-111119\nh2=222222-222229\nh3=333333-333339\nh4=444444-444449\nheader_protection_key=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f\ncontent_padding_addition=1-5\nrandom_trailers=true\n",
	} {
		t.Run(name, func(t *testing.T) { encryptedRoundtrip(t, params) })
	}
}
func encryptedRoundtrip(t *testing.T, params string) {
	r, stack := testPeer(t, params)
	listener, e := stack.ListenTCPAddrPort(netip.MustParseAddrPort("10.23.0.2:9000"))
	if e != nil {
		t.Fatal(e)
	}
	defer listener.Close()
	go func() {
		c, e := listener.Accept()
		if e != nil {
			return
		}
		defer c.Close()
		b, e := io.ReadAll(c)
		if e == nil {
			_, _ = c.Write(b)
		}
	}()
	c := socksClient(t, r)
	request(t, c, 1, "10.23.0.2:9000")
	_, _ = c.Write([]byte("ping"))
	// A half-close must not discard the server response.
	if e = c.(*net.TCPConn).CloseWrite(); e != nil {
		t.Fatal(e)
	}
	reply := make([]byte, 4)
	if _, e = io.ReadFull(c, reply); e != nil || string(reply) != "ping" {
		t.Fatal("encrypted TCP roundtrip", e)
	}
	c.Close()
	echo, e := stack.ListenUDPAddrPort(netip.MustParseAddrPort("10.23.0.2:9001"))
	if e != nil {
		t.Fatal(e)
	}
	defer echo.Close()
	go func() {
		b := make([]byte, 200)
		n, a, e := echo.ReadFrom(b)
		if e == nil {
			_, _ = echo.WriteTo(b[:n], a)
		}
	}()
	control := socksClient(t, r)
	relay := request(t, control, 3, "0.0.0.0:0")
	addr, _ := net.ResolveUDPAddr("udp", relay)
	udp, e := net.DialUDP("udp", nil, addr)
	if e != nil {
		t.Fatal(e)
	}
	defer udp.Close()
	_ = udp.SetDeadline(time.Now().Add(10 * time.Second))
	header, _ := addressBytes("10.23.0.2:9001")
	_, _ = udp.Write(append(append([]byte{0, 0, 0}, header...), []byte("udp-ping")...))
	b := make([]byte, 200)
	n, e := udp.Read(b)
	if e != nil || !strings.HasSuffix(string(b[:n]), "udp-ping") {
		t.Fatal("encrypted UDP roundtrip", e)
	}
	stats, e := r.Stats()
	if e != nil || !strings.Contains(stats, "last_handshake_time_sec=") || strings.Contains(stats, "private_key") {
		t.Fatal("stats", e)
	}
	done := make(chan struct{})
	go func() { r.Close(); r.Close(); close(done) }()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("close leaked goroutines")
	}
	if _, e = r.Stats(); e == nil {
		t.Fatal("stats after stop")
	}
}
func TestRejectUnauthenticatedAndExternalListener(t *testing.T) {
	if _, e := Start(testConfig, "0.0.0.0:0", "n", "0123456789abcdef"); e == nil {
		t.Fatal("external listener accepted")
	}
	if _, e := Start(testConfig, "127.0.0.1:0", "n", "short"); e == nil {
		t.Fatal("weak credential accepted")
	}
	r, _ := testPeer(t, "")
	c, e := net.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", r.Port()))
	if e != nil {
		t.Fatal(e)
	}
	defer c.Close()
	c.SetDeadline(time.Now().Add(time.Second))
	c.Write([]byte{5, 1, 0})
	b := make([]byte, 2)
	io.ReadFull(c, b)
	if b[1] != 255 {
		t.Fatal("unauthenticated method allowed")
	}
}
