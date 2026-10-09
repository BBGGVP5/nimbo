package naivecore

import (
	"crypto/subtle"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"strconv"
	"time"
)

func socksReply(c net.Conn, code byte) { c.Write([]byte{5, code, 0, 1, 127, 0, 0, 1, 0, 0}) }
func (r *Runtime) authenticate(c net.Conn) bool {
	var h [2]byte
	if _, e := io.ReadFull(c, h[:]); e != nil || h[0] != 5 || h[1] == 0 {
		return false
	}
	methods := make([]byte, int(h[1]))
	if _, e := io.ReadFull(c, methods); e != nil {
		return false
	}
	auth := false
	for _, m := range methods {
		auth = auth || m == 2
	}
	if !auth {
		c.Write([]byte{5, 255})
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
	valid := subtle.ConstantTimeCompare(user, []byte(r.user))&subtle.ConstantTimeCompare(pass, []byte(r.pass)) == 1
	code := byte(1)
	if valid {
		code = 0
	}
	c.Write([]byte{1, code})
	return valid
}
func readDestination(r io.Reader) (string, error) {
	var h [1]byte
	if _, e := io.ReadFull(r, h[:]); e != nil {
		return "", e
	}
	var host string
	switch h[0] {
	case 1, 4:
		n := 4
		if h[0] == 4 {
			n = 16
		}
		b := make([]byte, n)
		if _, e := io.ReadFull(r, b); e != nil {
			return "", e
		}
		host = net.IP(b).String()
	case 3:
		if _, e := io.ReadFull(r, h[:]); e != nil {
			return "", e
		}
		b := make([]byte, int(h[0]))
		if _, e := io.ReadFull(r, b); e != nil {
			return "", e
		}
		host = string(b)
		if !validHost(host) {
			return "", errors.New("invalid destination")
		}
	default:
		return "", errors.New("invalid destination")
	}
	var p [2]byte
	if _, e := io.ReadFull(r, p[:]); e != nil {
		return "", e
	}
	port := binary.BigEndian.Uint16(p[:])
	if port == 0 {
		return "", errors.New("invalid port")
	}
	return net.JoinHostPort(host, strconv.Itoa(int(port))), nil
}
func (r *Runtime) serve(c net.Conn) {
	c.SetDeadline(time.Now().Add(15 * time.Second))
	if !r.authenticate(c) {
		return
	}
	var h [3]byte
	if _, e := io.ReadFull(c, h[:]); e != nil || h[0] != 5 || h[2] != 0 {
		return
	}
	destination, e := readDestination(c)
	if e != nil {
		socksReply(c, 8)
		return
	}
	// Standard Naive CONNECT is TCP-only. Never fall back to direct UDP.
	if h[1] != 1 {
		socksReply(c, 7)
		return
	}
	up, cancel, e := r.dial(destination)
	defer cancel()
	if e != nil {
		socksReply(c, 5)
		return
	}
	if !r.track(up) {
		return
	}
	defer r.untrack(up)
	socksReply(c, 0)
	c.SetDeadline(time.Time{})
	done := make(chan struct{})
	go func() { io.CopyBuffer(up, c, make([]byte, 16*1024)); up.Close(); close(done) }()
	io.CopyBuffer(c, up, make([]byte, 16*1024))
	c.Close()
	up.Close()
	<-done
}
