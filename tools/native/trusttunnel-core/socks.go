package trusttunnelcore

import (
	"context"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"net/netip"
	"strconv"
	"sync"
	"time"
)

// Minimal RFC 1928/1929 server: CONNECT and UDP ASSOCIATE, which is exactly
// what Xray's socks outbound uses. Bound to loopback only.

const (
	socksVersion     = 5
	authNone         = 0x00
	authPassword     = 0x02
	authUnacceptable = 0xFF
	cmdConnect       = 0x01
	cmdUDPAssociate  = 0x03
	atypIPv4         = 0x01
	atypDomain       = 0x03
	atypIPv6         = 0x04

	replySucceeded           = 0x00
	replyGeneralFailure      = 0x01
	replyHostUnreachable     = 0x04
	replyCommandNotSupported = 0x07
	replyAddressNotSupported = 0x08

	handshakeTimeout = 10 * time.Second
	maxUDPPacket     = 64 * 1024
)

type socksServer struct {
	ctx      context.Context
	listener net.Listener
	username string
	password string
	runtime  *Runtime

	mu     sync.Mutex
	conns  map[io.Closer]struct{}
	closed bool
}

func newSocksServer(ctx context.Context, listen, username, password string, rt *Runtime) (*socksServer, error) {
	ln, err := net.Listen("tcp", listen)
	if err != nil {
		return nil, err
	}
	return &socksServer{
		ctx: ctx, listener: ln, username: username, password: password, runtime: rt,
		conns: make(map[io.Closer]struct{}),
	}, nil
}

func (s *socksServer) port() int { return s.listener.Addr().(*net.TCPAddr).Port }

func (s *socksServer) track(c io.Closer) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		_ = c.Close()
		return false
	}
	s.conns[c] = struct{}{}
	return true
}

func (s *socksServer) untrack(c io.Closer) {
	s.mu.Lock()
	delete(s.conns, c)
	s.mu.Unlock()
	_ = c.Close()
}

func (s *socksServer) close() {
	s.mu.Lock()
	s.closed = true
	conns := s.conns
	s.conns = map[io.Closer]struct{}{}
	s.mu.Unlock()
	_ = s.listener.Close()
	for c := range conns {
		_ = c.Close()
	}
}

func (s *socksServer) serve() {
	for {
		conn, err := s.listener.Accept()
		if err != nil {
			return
		}
		if !s.track(conn) {
			continue
		}
		go func() {
			defer s.untrack(conn)
			s.handle(conn)
		}()
	}
}

func (s *socksServer) handle(conn net.Conn) {
	_ = conn.SetDeadline(time.Now().Add(handshakeTimeout))
	if err := s.authenticate(conn); err != nil {
		return
	}
	header := make([]byte, 3)
	if _, err := io.ReadFull(conn, header); err != nil || header[0] != socksVersion {
		return
	}
	host, port, err := readAddress(conn)
	if err != nil {
		_ = writeReply(conn, replyAddressNotSupported, nil)
		return
	}
	switch header[1] {
	case cmdConnect:
		s.connect(conn, host, port)
	case cmdUDPAssociate:
		s.associate(conn)
	default:
		_ = writeReply(conn, replyCommandNotSupported, nil)
	}
}

func (s *socksServer) authenticate(conn net.Conn) error {
	head := make([]byte, 2)
	if _, err := io.ReadFull(conn, head); err != nil || head[0] != socksVersion {
		return errors.New("bad greeting")
	}
	methods := make([]byte, head[1])
	if _, err := io.ReadFull(conn, methods); err != nil {
		return err
	}
	want := byte(authNone)
	if s.username != "" || s.password != "" {
		want = authPassword
	}
	offered := false
	for _, m := range methods {
		offered = offered || m == want
	}
	if !offered {
		_, _ = conn.Write([]byte{socksVersion, authUnacceptable})
		return errors.New("no acceptable method")
	}
	if _, err := conn.Write([]byte{socksVersion, want}); err != nil {
		return err
	}
	if want == authNone {
		return nil
	}
	// RFC 1929 sub-negotiation.
	ver := make([]byte, 2)
	if _, err := io.ReadFull(conn, ver); err != nil || ver[0] != 1 {
		return errors.New("bad auth version")
	}
	user := make([]byte, ver[1])
	if _, err := io.ReadFull(conn, user); err != nil {
		return err
	}
	plen := make([]byte, 1)
	if _, err := io.ReadFull(conn, plen); err != nil {
		return err
	}
	pass := make([]byte, plen[0])
	if _, err := io.ReadFull(conn, pass); err != nil {
		return err
	}
	if string(user) != s.username || string(pass) != s.password {
		_, _ = conn.Write([]byte{1, 1})
		return errors.New("auth rejected")
	}
	_, err := conn.Write([]byte{1, 0})
	return err
}

func (s *socksServer) connect(conn net.Conn, host string, port uint16) {
	dialCtx, cancel := context.WithTimeout(s.ctx, handshakeTimeout)
	remote, err := s.runtime.client.Dial(dialCtx, net.JoinHostPort(host, strconv.Itoa(int(port))))
	cancel()
	if err != nil {
		_ = writeReply(conn, replyHostUnreachable, nil)
		return
	}
	if !s.track(remote) {
		return
	}
	defer s.untrack(remote)
	if err := writeReply(conn, replySucceeded, nil); err != nil {
		return
	}
	_ = conn.SetDeadline(time.Time{})
	done := make(chan struct{}, 2)
	go func() {
		_, _ = io.Copy(remote, conn)
		if cw, ok := remote.(interface{ CloseWrite() error }); ok {
			_ = cw.CloseWrite()
		}
		done <- struct{}{}
	}()
	go func() {
		_, _ = io.Copy(conn, remote)
		if cw, ok := conn.(*net.TCPConn); ok {
			_ = cw.CloseWrite()
		}
		done <- struct{}{}
	}()
	<-done
	// Give the other direction a moment to drain a half-closed stream.
	select {
	case <-done:
	case <-time.After(5 * time.Second):
	case <-s.ctx.Done():
	}
}

func (s *socksServer) associate(conn net.Conn) {
	relay, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		_ = writeReply(conn, replyGeneralFailure, nil)
		return
	}
	if !s.track(relay) {
		return
	}
	defer s.untrack(relay)
	listenCtx, cancel := context.WithTimeout(s.ctx, handshakeTimeout)
	remote, err := s.runtime.client.ListenPacket(listenCtx)
	cancel()
	if err != nil {
		_ = writeReply(conn, replyHostUnreachable, nil)
		return
	}
	if !s.track(remote) {
		return
	}
	defer s.untrack(remote)
	if err := writeReply(conn, replySucceeded, relay.LocalAddr().(*net.UDPAddr)); err != nil {
		return
	}
	_ = conn.SetDeadline(time.Time{})

	var clientMu sync.Mutex
	var client *net.UDPAddr
	go func() {
		buf := make([]byte, maxUDPPacket)
		for {
			n, from, err := relay.ReadFromUDP(buf)
			if err != nil {
				return
			}
			if !from.IP.IsLoopback() {
				continue
			}
			clientMu.Lock()
			client = from
			clientMu.Unlock()
			target, payload, err := s.parseDatagram(buf[:n])
			if err != nil {
				continue
			}
			_, _ = remote.WriteTo(payload, target)
		}
	}()
	go func() {
		buf := make([]byte, maxUDPPacket)
		for {
			n, from, err := remote.ReadFrom(buf)
			if err != nil {
				return
			}
			clientMu.Lock()
			to := client
			clientMu.Unlock()
			source, ok := from.(*net.UDPAddr)
			if to == nil || !ok {
				continue
			}
			_, _ = relay.WriteToUDP(append(datagramHeader(source), buf[:n]...), to)
		}
	}()
	// The association lives exactly as long as its TCP control connection.
	_, _ = io.Copy(io.Discard, conn)
}

// parseDatagram decodes RSV(2) FRAG(1) ATYP ADDR PORT DATA. TrustTunnel
// carries UDP destinations as IP addresses, so domains are resolved here
// through a protected resolver.
func (s *socksServer) parseDatagram(b []byte) (*net.UDPAddr, []byte, error) {
	if len(b) < 4 || b[2] != 0 {
		return nil, nil, errors.New("fragmented or short datagram")
	}
	r := &sliceReader{b: b[3:]}
	host, port, err := readAddress(r)
	if err != nil {
		return nil, nil, err
	}
	payload := r.b
	addr, perr := netip.ParseAddr(host)
	if perr != nil {
		ctx, cancel := context.WithTimeout(s.ctx, 5*time.Second)
		ips, err := s.runtime.resolver.LookupNetIP(ctx, "ip", host)
		cancel()
		if err != nil || len(ips) == 0 {
			return nil, nil, errors.New("cannot resolve datagram destination")
		}
		addr = ips[0]
		for _, ip := range ips {
			if ip.Is4() {
				addr = ip
				break
			}
		}
	}
	return net.UDPAddrFromAddrPort(netip.AddrPortFrom(addr.Unmap(), port)), payload, nil
}

func datagramHeader(source *net.UDPAddr) []byte {
	header := []byte{0, 0, 0}
	return append(header, encodeAddress(source)...)
}

type sliceReader struct{ b []byte }

func (r *sliceReader) Read(p []byte) (int, error) {
	if len(r.b) == 0 {
		return 0, io.EOF
	}
	n := copy(p, r.b)
	r.b = r.b[n:]
	return n, nil
}

func readAddress(r io.Reader) (string, uint16, error) {
	atyp := make([]byte, 1)
	if _, err := io.ReadFull(r, atyp); err != nil {
		return "", 0, err
	}
	var host string
	switch atyp[0] {
	case atypIPv4:
		ip := make([]byte, 4)
		if _, err := io.ReadFull(r, ip); err != nil {
			return "", 0, err
		}
		host = net.IP(ip).String()
	case atypIPv6:
		ip := make([]byte, 16)
		if _, err := io.ReadFull(r, ip); err != nil {
			return "", 0, err
		}
		host = net.IP(ip).String()
	case atypDomain:
		l := make([]byte, 1)
		if _, err := io.ReadFull(r, l); err != nil {
			return "", 0, err
		}
		name := make([]byte, l[0])
		if _, err := io.ReadFull(r, name); err != nil {
			return "", 0, err
		}
		host = string(name)
	default:
		return "", 0, errors.New("unsupported address type")
	}
	p := make([]byte, 2)
	if _, err := io.ReadFull(r, p); err != nil {
		return "", 0, err
	}
	return host, binary.BigEndian.Uint16(p), nil
}

func encodeAddress(addr *net.UDPAddr) []byte {
	var out []byte
	if ip4 := addr.IP.To4(); ip4 != nil {
		out = append([]byte{atypIPv4}, ip4...)
	} else {
		out = append([]byte{atypIPv6}, addr.IP.To16()...)
	}
	return binary.BigEndian.AppendUint16(out, uint16(addr.Port))
}

func writeReply(conn net.Conn, code byte, bound *net.UDPAddr) error {
	if bound == nil {
		bound = &net.UDPAddr{IP: net.IPv4zero, Port: 0}
	}
	reply := append([]byte{socksVersion, code, 0}, encodeAddress(bound)...)
	_, err := conn.Write(reply)
	return err
}
