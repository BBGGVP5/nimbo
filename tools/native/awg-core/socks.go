package awgcore

import (
	"context"
	"crypto/subtle"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"strconv"
	"sync"
	"time"
)

func readAddress(r io.Reader) (string, error) {
	var b [1]byte
	if _, e := io.ReadFull(r, b[:]); e != nil {
		return "", e
	}
	var host string
	switch b[0] {
	case 1:
		ip := make([]byte, 4)
		if _, e := io.ReadFull(r, ip); e != nil {
			return "", e
		}
		host = net.IP(ip).String()
	case 4:
		ip := make([]byte, 16)
		if _, e := io.ReadFull(r, ip); e != nil {
			return "", e
		}
		host = net.IP(ip).String()
	case 3:
		if _, e := io.ReadFull(r, b[:]); e != nil || b[0] == 0 {
			return "", errors.New("invalid domain")
		}
		s := make([]byte, int(b[0]))
		if _, e := io.ReadFull(r, s); e != nil {
			return "", e
		}
		for _, c := range s {
			if c < 33 || c > 126 {
				return "", errors.New("invalid domain")
			}
		}
		host = string(s)
	default:
		return "", errors.New("unsupported address type")
	}
	var port [2]byte
	if _, e := io.ReadFull(r, port[:]); e != nil {
		return "", e
	}
	return net.JoinHostPort(host, strconv.Itoa(int(binary.BigEndian.Uint16(port[:])))), nil
}
func addressBytes(address string) ([]byte, error) {
	host, port, e := net.SplitHostPort(address)
	if e != nil {
		return nil, e
	}
	p, e := strconv.ParseUint(port, 10, 16)
	if e != nil {
		return nil, e
	}
	var b []byte
	if ip := net.ParseIP(host); ip != nil {
		if v4 := ip.To4(); v4 != nil {
			b = append([]byte{1}, v4...)
		} else {
			b = append([]byte{4}, ip.To16()...)
		}
	} else {
		if len(host) == 0 || len(host) > 255 {
			return nil, errors.New("invalid domain")
		}
		b = append([]byte{3, byte(len(host))}, []byte(host)...)
	}
	return append(b, byte(p>>8), byte(p)), nil
}
func reply(c net.Conn, code byte, address string) {
	b, e := addressBytes(address)
	if e != nil {
		b = []byte{1, 0, 0, 0, 0, 0, 0}
	}
	_, _ = c.Write(append([]byte{5, code, 0}, b...))
}
func (r *Runtime) authenticate(c net.Conn) bool {
	var h [2]byte
	if _, e := io.ReadFull(c, h[:]); e != nil || h[0] != 5 || h[1] == 0 {
		return false
	}
	methods := make([]byte, int(h[1]))
	if _, e := io.ReadFull(c, methods); e != nil {
		return false
	}
	has := false
	for _, m := range methods {
		has = has || m == 2
	}
	if !has {
		_, _ = c.Write([]byte{5, 255})
		return false
	}
	if _, e := c.Write([]byte{5, 2}); e != nil {
		return false
	}
	if _, e := io.ReadFull(c, h[:]); e != nil || h[0] != 1 || h[1] == 0 {
		return false
	}
	user := make([]byte, int(h[1]))
	if _, e := io.ReadFull(c, user); e != nil {
		return false
	}
	var n [1]byte
	if _, e := io.ReadFull(c, n[:]); e != nil || n[0] == 0 {
		return false
	}
	pass := make([]byte, int(n[0]))
	if _, e := io.ReadFull(c, pass); e != nil {
		return false
	}
	valid := (subtle.ConstantTimeCompare(user, []byte(r.user)) & subtle.ConstantTimeCompare(pass, []byte(r.pass))) == 1
	status := byte(1)
	if valid {
		status = 0
	}
	_, _ = c.Write([]byte{1, status})
	return valid
}
func (r *Runtime) serve(c net.Conn) {
	_ = c.SetDeadline(time.Now().Add(15 * time.Second))
	if !r.authenticate(c) {
		return
	}
	var h [3]byte
	if _, e := io.ReadFull(c, h[:]); e != nil || h[0] != 5 || h[2] != 0 {
		return
	}
	dest, e := readAddress(c)
	if e != nil {
		reply(c, 8, "127.0.0.1:0")
		return
	}
	switch h[1] {
	case 1:
		ctx, cancel := context.WithTimeout(r.ctx, 15*time.Second)
		up, e := r.stack.DialContext(ctx, "tcp", dest)
		cancel()
		if e != nil {
			reply(c, 5, "127.0.0.1:0")
			return
		}
		if !r.track(up) {
			return
		}
		defer r.untrack(up)
		reply(c, 0, "127.0.0.1:0")
		_ = c.SetDeadline(time.Time{})
		done := make(chan struct{})
		go func() {
			_, err := io.Copy(up, c)
			if half, ok := up.(interface{ CloseWrite() error }); ok && err == nil {
				_ = half.CloseWrite()
			} else {
				_ = up.Close()
			}
			close(done)
		}()
		_, _ = io.Copy(c, up)
		_ = c.Close()
		_ = up.Close()
		<-done
	case 3:
		r.serveUDP(c, dest)
	default:
		reply(c, 7, "127.0.0.1:0")
	}
}

// UDP relays are bound to the authenticated TCP control connection and its IP.
// FRAG is deliberately rejected. Flows and associations have strict local bounds.
func (r *Runtime) serveUDP(control net.Conn, requested string) {
	peer := control.RemoteAddr().(*net.TCPAddr).IP
	h, p, _ := net.SplitHostPort(requested)
	requestedIP := net.ParseIP(h)
	requestedPort, _ := strconv.Atoi(p)
	if requestedIP == nil || (!requestedIP.IsUnspecified() && !requestedIP.Equal(peer)) {
		reply(control, 2, "127.0.0.1:0")
		return
	}
	local := control.LocalAddr().(*net.TCPAddr).IP
	udp, e := net.ListenUDP("udp", &net.UDPAddr{IP: local})
	if e != nil {
		reply(control, 1, "127.0.0.1:0")
		return
	}
	defer udp.Close()
	if !r.track(udp) {
		return
	}
	defer r.untrack(udp)
	reply(control, 0, udp.LocalAddr().String())
	_ = control.SetDeadline(time.Time{})
	ctx, cancel := context.WithCancel(r.ctx)
	defer cancel()
	var mu sync.Mutex
	flows := map[string]net.Conn{}
	var wg sync.WaitGroup
	defer func() {
		cancel()
		mu.Lock()
		for _, c := range flows {
			_ = c.Close()
		}
		mu.Unlock()
		wg.Wait()
	}()
	done := make(chan struct{})
	go func() { _, _ = io.Copy(io.Discard, control); _ = udp.Close(); close(done) }()
	defer func() { _ = control.Close(); <-done }()
	boundPort := requestedPort
	buffer := make([]byte, 65535)
	for {
		n, source, e := udp.ReadFromUDP(buffer)
		if e != nil {
			return
		}
		if !source.IP.Equal(peer) || n < 4 || buffer[0] != 0 || buffer[1] != 0 || buffer[2] != 0 {
			continue
		}
		if boundPort != 0 && source.Port != boundPort {
			continue
		}
		reader := &byteReader{b: buffer[3:n]}
		dest, e := readAddress(reader)
		if e != nil {
			continue
		}
		payload := buffer[n-len(reader.b) : n]
		if boundPort == 0 {
			boundPort = source.Port
		}
		target := *source
		mu.Lock()
		up := flows[dest]
		full := len(flows) >= 128
		mu.Unlock()
		if up == nil {
			if full {
				continue
			}
			dialCtx, stop := context.WithTimeout(ctx, 5*time.Second)
			up, e = r.stack.DialContext(dialCtx, "udp", dest)
			stop()
			if e != nil {
				continue
			}
			mu.Lock()
			flows[dest] = up
			mu.Unlock()
			wg.Add(1)
			go func(address string, c net.Conn, target net.UDPAddr) {
				defer wg.Done()
				defer c.Close()
				defer func() { mu.Lock(); delete(flows, address); mu.Unlock() }()
				data := make([]byte, 65507)
				header, _ := addressBytes(address)
				for {
					_ = c.SetReadDeadline(time.Now().Add(60 * time.Second))
					n, e := c.Read(data)
					if e != nil {
						return
					}
					packet := append(append([]byte{0, 0, 0}, header...), data[:n]...)
					if _, e = udp.WriteToUDP(packet, &target); e != nil {
						return
					}
				}
			}(dest, up, target)
		}
		_ = up.SetWriteDeadline(time.Now().Add(5 * time.Second))
		_, _ = up.Write(payload)
	}
}

type byteReader struct{ b []byte }

func (r *byteReader) Read(p []byte) (int, error) {
	if len(r.b) == 0 {
		return 0, io.EOF
	}
	n := copy(p, r.b)
	r.b = r.b[n:]
	return n, nil
}
