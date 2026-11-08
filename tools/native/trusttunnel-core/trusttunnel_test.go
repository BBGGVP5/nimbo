package trusttunnelcore

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"encoding/pem"
	"errors"
	"io"
	"math/big"
	"net"
	"strconv"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/transport/trusttunnel"
	"github.com/metacubex/sing/common/buf"
	"github.com/metacubex/sing/common/logger"
	M "github.com/metacubex/sing/common/metadata"
	N "github.com/metacubex/sing/common/network"
	"github.com/metacubex/tls"
)

// directHandler is the endpoint side: it forwards to the real destination,
// like a TrustTunnel endpoint does.
type directHandler struct{}

func (directHandler) NewConnection(ctx context.Context, conn net.Conn, metadata M.Metadata) error {
	remote, err := net.Dial("tcp", metadata.Destination.String())
	if err != nil {
		return err
	}
	defer remote.Close()
	defer conn.Close()
	go func() {
		_, _ = io.Copy(remote, conn)
		// Either side ending tears down both, so server shutdown never waits.
		_ = remote.Close()
	}()
	_, _ = io.Copy(conn, remote)
	return nil
}

func (directHandler) NewPacketConnection(ctx context.Context, conn N.PacketConn, metadata M.Metadata) error {
	socket, err := net.ListenUDP("udp", nil)
	if err != nil {
		return err
	}
	defer socket.Close()
	go func() {
		<-ctx.Done()
		_ = conn.Close()
	}()
	go func() {
		b := make([]byte, 65535)
		for {
			n, from, err := socket.ReadFromUDP(b)
			if err != nil {
				return
			}
			if conn.WritePacket(buf.As(b[:n]).ToOwned(), M.SocksaddrFromNet(from)) != nil {
				return
			}
		}
	}()
	for {
		packet := buf.NewPacket()
		destination, err := conn.ReadPacket(packet)
		if err != nil {
			packet.Release()
			return nil
		}
		_, _ = socket.WriteToUDP(packet.Bytes(), destination.UDPAddr())
		packet.Release()
	}
}

type endpoint struct {
	address string
	certPEM string
	close   func()
}

func selfSigned(t *testing.T, name string) (tls.Certificate, string) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	template := &x509.Certificate{
		SerialNumber:          big.NewInt(time.Now().UnixNano()),
		Subject:               pkix.Name{CommonName: name},
		DNSNames:              []string{name},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		IsCA:                  true,
		BasicConstraintsValid: true,
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	keyDER, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyDER})
	pair, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil {
		t.Fatal(err)
	}
	return pair, string(certPEM)
}

func startEndpoint(t *testing.T, http3 bool) endpoint {
	t.Helper()
	pair, certPEM := selfSigned(t, "tt.test")
	ctx, cancel := context.WithCancel(context.Background())
	service := trusttunnel.NewService(trusttunnel.ServiceOptions{
		Ctx: ctx, Logger: logger.NOP(), Handler: directHandler{},
	})
	service.UpdateUsers(map[string]string{"alice": "s3cret"})
	tlsConfig := &tls.Config{Certificates: []tls.Certificate{pair}}
	var address string
	if http3 {
		udp, err := net.ListenPacket("udp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		address = udp.LocalAddr().String()
		if err := service.Start(nil, udp, tlsConfig); err != nil {
			t.Fatal(err)
		}
	} else {
		ln, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		address = ln.Addr().String()
		if err := service.Start(ln, nil, tlsConfig); err != nil {
			t.Fatal(err)
		}
	}
	return endpoint{address: address, certPEM: certPEM, close: func() { _ = service.Close(); cancel() }}
}

func tcpEcho(t *testing.T) string {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = ln.Close() })
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func() { defer c.Close(); _, _ = io.Copy(c, c) }()
		}
	}()
	return ln.Addr().String()
}

func udpEcho(t *testing.T) *net.UDPAddr {
	t.Helper()
	pc, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = pc.Close() })
	go func() {
		b := make([]byte, 2048)
		for {
			n, from, err := pc.ReadFromUDP(b)
			if err != nil {
				return
			}
			_, _ = pc.WriteToUDP(b[:n], from)
		}
	}()
	return pc.LocalAddr().(*net.UDPAddr)
}

func request(ep endpoint, http3 bool) Request {
	return Request{
		Server: ep.address, SNI: "tt.test", Username: "alice", Password: "s3cret", HTTP3: http3,
		Certificate: ep.certPEM, SocksUsername: "lu", SocksPassword: "lp", TimeoutMs: 5000,
	}
}

func socksHandshake(t *testing.T, conn net.Conn, cmd byte, host string, port int) []byte {
	t.Helper()
	_ = conn.SetDeadline(time.Now().Add(10 * time.Second))
	mustWrite(t, conn, []byte{5, 1, 2})
	expect(t, conn, []byte{5, 2})
	mustWrite(t, conn, append(append([]byte{1, 2}, "lu"...), append([]byte{2}, "lp"...)...))
	expect(t, conn, []byte{1, 0})
	req := []byte{5, cmd, 0, 1}
	req = append(req, net.ParseIP(host).To4()...)
	req = binary.BigEndian.AppendUint16(req, uint16(port))
	mustWrite(t, conn, req)
	reply := make([]byte, 10)
	if _, err := io.ReadFull(conn, reply); err != nil {
		t.Fatal(err)
	}
	if reply[1] != 0 {
		t.Fatalf("socks reply %d", reply[1])
	}
	return reply
}

func mustWrite(t *testing.T, w io.Writer, b []byte) {
	t.Helper()
	if _, err := w.Write(b); err != nil {
		t.Fatal(err)
	}
}

func expect(t *testing.T, r io.Reader, want []byte) {
	t.Helper()
	got := make([]byte, len(want))
	if _, err := io.ReadFull(r, got); err != nil || !bytes.Equal(got, want) {
		t.Fatalf("got %v err %v, want %v", got, err, want)
	}
}

// Mihomo's global hook rejects every socket while no Mihomo session runs; the
// TrustTunnel client must not depend on it, but must use our protector.
func installRejectingMihomoHook(t *testing.T) {
	previous := dialer.DefaultSocketHook
	dialer.DefaultSocketHook = func(string, string, syscall.RawConn) error {
		return errors.New("Nimbo runtime is stopped")
	}
	t.Cleanup(func() { dialer.DefaultSocketHook = previous })
}

func runTunnel(t *testing.T, http3 bool) {
	installRejectingMihomoHook(t)
	ep := startEndpoint(t, http3)
	defer ep.close()
	var protected atomic.Int32
	rt, err := Start(request(ep, http3), func(uintptr) bool { protected.Add(1); return true })
	if err != nil {
		t.Fatalf("start: %v", err)
	}
	defer rt.Close()
	if protected.Load() == 0 {
		t.Fatal("endpoint socket was not passed to the protector")
	}

	echo := tcpEcho(t)
	host, portText, _ := net.SplitHostPort(echo)
	port, _ := strconv.Atoi(portText)
	conn, err := net.Dial("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(rt.Port())))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	socksHandshake(t, conn, cmdConnect, host, port)
	payload := []byte("hello through trusttunnel")
	mustWrite(t, conn, payload)
	expect(t, conn, payload)

	// UDP ASSOCIATE through the same tunnel.
	udpTarget := udpEcho(t)
	control, err := net.Dial("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(rt.Port())))
	if err != nil {
		t.Fatal(err)
	}
	defer control.Close()
	reply := socksHandshake(t, control, cmdUDPAssociate, "0.0.0.0", 0)
	relay := &net.UDPAddr{IP: net.IP(reply[4:8]), Port: int(binary.BigEndian.Uint16(reply[8:10]))}
	client, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	datagram := append(datagramHeader(udpTarget), []byte("udp ping")...)
	got := make([]byte, 2048)
	var answered bool
	for attempt := 0; attempt < 10 && !answered; attempt++ {
		mustWrite(t, writerTo{client, relay}, datagram)
		_ = client.SetReadDeadline(time.Now().Add(time.Second))
		n, err := client.Read(got)
		if err != nil {
			continue
		}
		answered = bytes.Equal(got[:n], datagram)
	}
	if !answered {
		t.Fatal("no UDP echo through the tunnel")
	}
}

type writerTo struct {
	c  *net.UDPConn
	to *net.UDPAddr
}

func (w writerTo) Write(b []byte) (int, error) { return w.c.WriteToUDP(b, w.to) }

func TestHTTP2TunnelCarriesTCPAndUDP(t *testing.T) { runTunnel(t, false) }

func TestHTTP3TunnelCarriesTCPAndUDP(t *testing.T) { runTunnel(t, true) }

func TestWrongPasswordIsReportedAsAuthRejected(t *testing.T) {
	ep := startEndpoint(t, false)
	defer ep.close()
	r := request(ep, false)
	r.Password = "wrong"
	_, err := Start(r, nil)
	var typed *Error
	if !errors.As(err, &typed) || typed.Code != "AUTH_REJECTED" {
		t.Fatalf("got %v", err)
	}
}

func TestForeignCertificateIsRejected(t *testing.T) {
	ep := startEndpoint(t, false)
	defer ep.close()
	_, otherPEM := selfSigned(t, "tt.test")
	r := request(ep, false)
	r.Certificate = otherPEM
	_, err := Start(r, nil)
	var typed *Error
	if !errors.As(err, &typed) || typed.Code != "TLS_VERIFY" {
		t.Fatalf("got %v", err)
	}
}

func TestSocksRequiresItsCredentials(t *testing.T) {
	ep := startEndpoint(t, false)
	defer ep.close()
	rt, err := Start(request(ep, false), nil)
	if err != nil {
		t.Fatal(err)
	}
	defer rt.Close()
	conn, err := net.Dial("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(rt.Port())))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
	mustWrite(t, conn, []byte{5, 1, 0})
	expect(t, conn, []byte{5, 0xFF})
}

func TestJSONSurfaceAndProbe(t *testing.T) {
	ep := startEndpoint(t, true)
	defer ep.close()
	raw, _ := json.Marshal(request(ep, true))
	var probe struct {
		OK        bool  `json:"ok"`
		LatencyMs int64 `json:"latencyMs"`
	}
	if err := json.Unmarshal([]byte(ProbeJSON(string(raw), nil)), &probe); err != nil || !probe.OK || probe.LatencyMs < 1 {
		t.Fatalf("probe %+v %v", probe, err)
	}
	var started struct {
		OK      bool   `json:"ok"`
		Port    int    `json:"port"`
		Version string `json:"version"`
	}
	if err := json.Unmarshal([]byte(StartJSON(string(raw), nil)), &started); err != nil || !started.OK || started.Port == 0 || started.Version != Version {
		t.Fatalf("start %+v %v", started, err)
	}
	if !bytes.Contains([]byte(StatusJSON()), []byte(`"running":true`)) {
		t.Fatal("not running")
	}
	NetworkChanged()
	StopJSON()
	if !bytes.Contains([]byte(StatusJSON()), []byte(`"running":false`)) {
		t.Fatal("still running")
	}
	if !bytes.Contains([]byte(StartJSON(`{"server":"example.com:443"}`, nil)), []byte(`"error":"REQUEST"`)) {
		t.Fatal("unresolved server accepted")
	}
}

func TestLinkRequestResolvesAndConnects(t *testing.T) {
	ep := startEndpoint(t, false)
	defer ep.close()
	host, port, _ := net.SplitHostPort(ep.address)
	block, _ := pem.Decode([]byte(ep.certPEM))
	tlv := func(tag byte, v []byte) []byte { return append([]byte{tag, byte(len(v))}, v...) }
	payload := append(tlv(0x01, []byte("tt.test")), tlv(0x02, []byte(net.JoinHostPort(host, port)))...)
	payload = append(payload, tlv(0x05, []byte("alice"))...)
	payload = append(payload, tlv(0x06, []byte("s3cret"))...)
	// Certificates exceed 63 bytes: two-byte varint length.
	payload = append(payload, 0x08, byte(0x40|len(block.Bytes)>>8), byte(len(block.Bytes)))
	payload = append(payload, block.Bytes...)
	link := "tt://?" + base64.RawURLEncoding.EncodeToString(payload) + "#Test"
	parsed, err := ParseLink(link)
	if err != nil || parsed.Hostname != "tt.test" || parsed.CertificatePEM != ep.certPEM {
		t.Fatalf("parse %+v %v", parsed, err)
	}
	if _, err := Probe(Request{Link: link, TimeoutMs: 5000}, nil); err != nil {
		t.Fatalf("probe via link: %v", err)
	}
	if _, err := ParseLink("tt://?AAEDAQFoAgVoOjQ0MwUBdQYBcA"); err == nil {
		t.Fatal("newer link version accepted")
	}
}
