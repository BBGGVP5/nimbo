package awgcore

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun/netstack"
)

type Runtime struct {
	dev         *device.Device
	stack       *netstack.Net
	listener    net.Listener
	user, pass  string
	ctx         context.Context
	cancel      context.CancelFunc
	mu          sync.Mutex
	closed      bool
	connections map[net.Conn]struct{}
	wg          sync.WaitGroup
	once        sync.Once
}

func Start(configText, listen, username, password string) (*Runtime, error) {
	if len(username) < 1 || len(username) > 255 || len(password) < 16 || len(password) > 255 {
		return nil, errors.New("SOCKS credentials required (password minimum 16 bytes)")
	}
	cfg, e := ParseConfig(configText)
	if e != nil {
		return nil, e
	}
	host, port, e := net.SplitHostPort(listen)
	ip, ipErr := netip.ParseAddr(host)
	if e != nil || ipErr != nil || !ip.IsLoopback() {
		return nil, errors.New("SOCKS listen address must be numeric loopback")
	}
	if _, e = strconv.ParseUint(port, 10, 16); e != nil {
		return nil, errors.New("invalid SOCKS port")
	}
	// Resolve the encrypted outer endpoint using the physical network before creating the inner stack.
	eh, ep, _ := net.SplitHostPort(cfg.Endpoint)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	addrs, e := net.DefaultResolver.LookupNetIP(ctx, "ip", eh)
	cancel()
	if e != nil || len(addrs) == 0 {
		return nil, errors.New("could not resolve AWG endpoint")
	}
	// LookupNetIP may return IPv4-mapped IPv6; the AWG bind must use its IPv4 socket.
	endpoint := net.JoinHostPort(addrs[0].Unmap().String(), ep)
	tun, stack, e := netstack.CreateNetTUN(cfg.Addresses, cfg.DNS, cfg.MTU)
	if e != nil {
		return nil, errors.New("could not create AWG network stack")
	}
	dev := device.NewDevice(tun, conn.NewDefaultBind(), device.NewLogger(device.LogLevelSilent, ""))
	if e = dev.IpcSet(cfg.UAPI + "endpoint=" + endpoint + "\n"); e != nil {
		dev.Close()
		return nil, errors.New("AWG engine rejected configuration")
	}
	if e = dev.Up(); e != nil {
		dev.Close()
		return nil, errors.New("could not start AWG engine")
	}
	listener, e := net.Listen("tcp", listen)
	if e != nil {
		dev.Close()
		return nil, errors.New("could not listen on local SOCKS address")
	}
	runtimeCtx, runtimeCancel := context.WithCancel(context.Background())
	r := &Runtime{dev: dev, stack: stack, listener: listener, user: username, pass: password, ctx: runtimeCtx, cancel: runtimeCancel, connections: make(map[net.Conn]struct{})}
	r.wg.Add(1)
	go r.accept()
	return r, nil
}
func (r *Runtime) Port() int { return r.listener.Addr().(*net.TCPAddr).Port }
func (r *Runtime) Stats() (string, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.closed {
		return "", errors.New("AWG stopped")
	}
	s, e := r.dev.IpcGet()
	if e != nil {
		return "", errors.New("AWG stats unavailable")
	}
	var safe []string
	for _, line := range strings.Split(s, "\n") {
		if strings.HasPrefix(line, "rx_bytes=") || strings.HasPrefix(line, "tx_bytes=") || strings.HasPrefix(line, "last_handshake_time_sec=") {
			safe = append(safe, line)
		}
	}
	return strings.Join(safe, "\n"), nil
}
func (r *Runtime) Close() {
	r.once.Do(func() {
		r.mu.Lock()
		r.closed = true
		r.cancel()
		_ = r.listener.Close()
		for c := range r.connections {
			_ = c.Close()
		}
		r.mu.Unlock()
		r.dev.Close()
		r.wg.Wait()
	})
}
func (r *Runtime) track(c net.Conn) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.closed {
		_ = c.Close()
		return false
	}
	r.connections[c] = struct{}{}
	return true
}
func (r *Runtime) untrack(c net.Conn) {
	r.mu.Lock()
	delete(r.connections, c)
	r.mu.Unlock()
	_ = c.Close()
}
func (r *Runtime) accept() {
	defer r.wg.Done()
	slots := make(chan struct{}, 128)
	for {
		c, e := r.listener.Accept()
		if e != nil {
			return
		}
		select {
		case slots <- struct{}{}:
		default:
			_ = c.Close()
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
