package mihomocore

import (
	"context"
	"crypto/subtle"
	"fmt"
	"io"
	"net"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/inbound"
	authStore "github.com/metacubex/mihomo/listener/auth"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/mixed"
	"github.com/metacubex/mihomo/tunnel"
)

func loopbackAddress(address string) error {
	host, port, err := net.SplitHostPort(address)
	if err != nil {
		return problem("UNSAFE_LISTEN", "options", "explicit numeric loopback host:port required")
	}
	ip := net.ParseIP(host)
	n, e := strconv.Atoi(port)
	if ip == nil || !ip.IsLoopback() || e != nil || n < 0 || n > 65535 {
		return problem("UNSAFE_LISTEN", "options", "only loopback and port 0..65535 allowed")
	}
	return nil
}

type trackedListener struct {
	net.Listener
	mu          sync.Mutex
	connections map[*trackedConn]struct{}
	closed      bool
}
type trackedConn struct {
	net.Conn
	owner *trackedListener
	once  sync.Once
}

func (c *trackedConn) Close() error {
	var err error
	c.once.Do(func() { err = c.Conn.Close(); c.owner.mu.Lock(); delete(c.owner.connections, c); c.owner.mu.Unlock() })
	return err
}
func (l *trackedListener) Accept() (net.Conn, error) {
	c, err := l.Listener.Accept()
	if err != nil {
		return nil, err
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.closed {
		_ = c.Close()
		return nil, net.ErrClosed
	}
	t := &trackedConn{Conn: c, owner: l}
	l.connections[t] = struct{}{}
	return t, nil
}
func (l *trackedListener) Close() error {
	l.mu.Lock()
	if l.closed {
		l.mu.Unlock()
		return nil
	}
	l.closed = true
	all := make([]*trackedConn, 0, len(l.connections))
	for c := range l.connections {
		all = append(all, c)
	}
	l.mu.Unlock()
	err := l.Listener.Close()
	for _, c := range all {
		_ = c.Close()
	}
	return err
}

type fixedListenConfig struct{ listener *trackedListener }

func (c fixedListenConfig) Listen(context.Context, string, string) (net.Listener, error) {
	return c.listener, nil
}
func (c fixedListenConfig) ListenPacket(context.Context, string, string) (net.PacketConn, error) {
	return nil, fmt.Errorf("UDP inbound unavailable in this managed TCP listener")
}
func (s *session) listenMixed(address string) error {
	l, err := net.Listen("tcp", address)
	if err != nil {
		return err
	}
	s.accepted = &trackedListener{Listener: l, connections: map[*trackedConn]struct{}{}}
	s.mixed, err = mixed.NewWithConfig(LC.AuthServer{Enable: true, Listen: address, AuthStore: authStore.Nil}, fixedListenConfig{s.accepted}, tunnel.Tunnel, inbound.WithInName("NIMBO-MIXED"))
	return err
}

type controlServer struct {
	server   *http.Server
	listener net.Listener
	address  string
}

func newController(address, secret string) (*controlServer, error) {
	l, err := net.Listen("tcp", address)
	if err != nil {
		return nil, err
	}
	c := &controlServer{listener: l, address: l.Addr().String()}
	c.server = &http.Server{ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, IdleTimeout: 15 * time.Second, MaxHeaderBytes: 8192, Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		if r.URL.Path != "/v1/invoke" {
			http.NotFound(w, r)
			return
		}
		if r.Method != http.MethodPost {
			w.WriteHeader(405)
			return
		}
		if r.Header.Get("Origin") != "" {
			w.WriteHeader(403)
			return
		}
		if subtle.ConstantTimeCompare([]byte(r.Header.Get("Authorization")), []byte("Bearer "+secret)) != 1 {
			w.WriteHeader(401)
			return
		}
		r.Body = http.MaxBytesReader(w, r.Body, maxRequest)
		defer r.Body.Close()
		b, err := io.ReadAll(r.Body)
		if err != nil {
			w.WriteHeader(413)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, Invoke(string(b)))
	})}
	go func() { _ = c.server.Serve(l) }()
	return c, nil
}
func (c *controlServer) close() {
	_ = c.listener.Close()
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = c.server.Shutdown(ctx)
	}()
}
