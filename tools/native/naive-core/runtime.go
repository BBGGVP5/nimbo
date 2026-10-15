package naivecore

import (
	"context"
	"errors"
	"net"
	"strconv"
	"sync"
	"time"
)

type Client interface {
	DialContext(context.Context, string) (net.Conn, error)
	Close() error
	CloseAllConnections()
}
type clientFactory func(context.Context, Config) (Client, error)
type Runtime struct {
	client         Client
	listener       net.Listener
	user, pass     string
	ctx            context.Context
	cancel         context.CancelFunc
	mu             sync.Mutex
	closed, failed bool
	connections    map[net.Conn]struct{}
	wg             sync.WaitGroup
	once           sync.Once
}

func Start(link, listen, user, pass string) (*Runtime, error) {
	return start(link, listen, user, pass, newNativeClient)
}
func start(link, listen, user, pass string, factory clientFactory) (*Runtime, error) {
	cfg, e := Parse(link)
	if e != nil {
		return nil, e
	}
	host, port, e := net.SplitHostPort(listen)
	if e != nil || host != "127.0.0.1" {
		return nil, errors.New("Naive listener must be IPv4 loopback")
	}
	if _, e = strconv.ParseUint(port, 10, 16); e != nil {
		return nil, errors.New("invalid local port")
	}
	if len(user) < 1 || len(user) > 255 || len(pass) < 16 || len(pass) > 255 {
		return nil, errors.New("local authentication required")
	}
	l, e := net.Listen("tcp4", listen)
	if e != nil {
		return nil, errors.New("Naive listener unavailable")
	}
	ctx, cancel := context.WithCancel(context.Background())
	client, e := factory(ctx, cfg)
	if e != nil {
		cancel()
		l.Close()
		return nil, errors.New("Naive native client failed to start")
	}
	r := &Runtime{client: client, listener: l, user: user, pass: pass, ctx: ctx, cancel: cancel, connections: make(map[net.Conn]struct{})}
	r.wg.Add(1)
	go r.accept()
	return r, nil
}
func (r *Runtime) Port() int     { return r.listener.Addr().(*net.TCPAddr).Port }
func (r *Runtime) Running() bool { r.mu.Lock(); defer r.mu.Unlock(); return !r.closed && !r.failed }
func (r *Runtime) Close() {
	r.once.Do(func() {
		r.mu.Lock()
		r.closed = true
		r.cancel()
		r.listener.Close()
		for c := range r.connections {
			c.Close()
		}
		r.mu.Unlock()
		r.client.Close()
		r.wg.Wait()
	})
}
func (r *Runtime) NetworkChanged() {
	r.mu.Lock()
	if r.closed {
		r.mu.Unlock()
		return
	}
	for c := range r.connections {
		c.Close()
	}
	r.mu.Unlock()
	r.client.CloseAllConnections()
}
func (r *Runtime) track(c net.Conn) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.closed {
		c.Close()
		return false
	}
	r.connections[c] = struct{}{}
	return true
}
func (r *Runtime) untrack(c net.Conn) {
	r.mu.Lock()
	delete(r.connections, c)
	r.mu.Unlock()
	c.Close()
}
func (r *Runtime) accept() {
	defer r.wg.Done()
	slots := make(chan struct{}, 64)
	for {
		c, e := r.listener.Accept()
		if e != nil {
			r.mu.Lock()
			r.failed = true
			r.mu.Unlock()
			return
		}
		select {
		case slots <- struct{}{}:
		default:
			c.Close()
			continue
		}
		if !r.track(c) {
			<-slots
			return
		}
		r.wg.Add(1)
		go func() { defer r.wg.Done(); defer func() { <-slots }(); defer r.untrack(c); r.serve(c) }()
	}
}
func (r *Runtime) dial(destination string) (net.Conn, context.CancelFunc, error) {
	ctx, cancel := context.WithCancel(r.ctx)
	timer := time.AfterFunc(15*time.Second, cancel)
	conn, err := r.client.DialContext(ctx, destination)
	timer.Stop()
	if err != nil {
		cancel()
	}
	return conn, cancel, err
}
