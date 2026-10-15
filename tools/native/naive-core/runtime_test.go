package naivecore

import (
	"context"
	"io"
	"net"
	"strconv"
	"sync/atomic"
	"testing"
	"time"
)

type testClient struct {
	dials  atomic.Int32
	closed atomic.Bool
	reset  atomic.Int32
}

func (c *testClient) DialContext(ctx context.Context, dest string) (net.Conn, error) {
	c.dials.Add(1)
	a, b := net.Pipe()
	go func() { defer b.Close(); io.Copy(b, b) }()
	return a, nil
}
func (c *testClient) Close() error         { c.closed.Store(true); return nil }
func (c *testClient) CloseAllConnections() { c.reset.Add(1) }

const testPassword = "random-local-test-password"

func fixture(t *testing.T) (*Runtime, *testClient) {
	t.Helper()
	c := &testClient{}
	r, e := start("naive+https://test:password@proxy.invalid", "127.0.0.1:0", "nimbo", testPassword, func(context.Context, Config) (Client, error) { return c, nil })
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(r.Close)
	return r, c
}
func local(t *testing.T, r *Runtime) net.Conn {
	t.Helper()
	c, e := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(r.Port())), time.Second)
	if e != nil {
		t.Fatal(e)
	}
	c.SetDeadline(time.Now().Add(2 * time.Second))
	t.Cleanup(func() { c.Close() })
	return c
}
func auth(t *testing.T, c net.Conn, pass string) byte {
	t.Helper()
	c.Write([]byte{5, 1, 2})
	b := make([]byte, 2)
	if _, e := io.ReadFull(c, b); e != nil {
		t.Fatal(e)
	}
	if b[1] != 2 {
		t.Fatal("auth method")
	}
	p := append([]byte{1, 5}, []byte("nimbo")...)
	p = append(p, byte(len(pass)))
	p = append(p, []byte(pass)...)
	c.Write(p)
	if _, e := io.ReadFull(c, b); e != nil {
		t.Fatal(e)
	}
	return b[1]
}
func connect(t *testing.T, c net.Conn, command byte) byte {
	t.Helper()
	dest := "example.invalid"
	q := append([]byte{5, command, 0, 3, byte(len(dest))}, []byte(dest)...)
	q = append(q, 1, 187)
	c.Write(q)
	b := make([]byte, 10)
	if _, e := io.ReadFull(c, b); e != nil {
		t.Fatal(e)
	}
	return b[1]
}
func TestLocalConnectAndShutdown(t *testing.T) {
	r, client := fixture(t)
	c := local(t, r)
	if auth(t, c, testPassword) != 0 || connect(t, c, 1) != 0 {
		t.Fatal("connect failed")
	}
	c.Write([]byte("payload"))
	b := make([]byte, 7)
	if _, e := io.ReadFull(c, b); e != nil || string(b) != "payload" {
		t.Fatal("proxy copy")
	}
	r.Close()
	r.Close()
	if r.Running() || !client.closed.Load() || client.dials.Load() != 1 {
		t.Fatal("shutdown/route")
	}
	if _, e := c.Read(b); e == nil {
		t.Fatal("socket survived stop")
	}
}
func TestAuthenticationAndUDPFailClosed(t *testing.T) {
	r, client := fixture(t)
	c := local(t, r)
	if auth(t, c, "wrong-password") == 0 {
		t.Fatal("bad auth accepted")
	}
	c.Close()
	c = local(t, r)
	c.Write([]byte{5, 1, 0})
	b := make([]byte, 2)
	io.ReadFull(c, b)
	if b[1] != 255 {
		t.Fatal("anonymous auth accepted")
	}
	c.Close()
	c = local(t, r)
	if auth(t, c, testPassword) != 0 || connect(t, c, 3) != 7 {
		t.Fatal("UDP not rejected")
	}
	if client.dials.Load() != 0 {
		t.Fatal("rejected command dialed")
	}
}
func TestResetKeepsListenerAndCredentials(t *testing.T) {
	r, c := fixture(t)
	sock := local(t, r)
	if auth(t, sock, testPassword) != 0 || connect(t, sock, 1) != 0 {
		t.Fatal("connect")
	}
	r.NetworkChanged()
	b := make([]byte, 1)
	if _, e := sock.Read(b); e == nil {
		t.Fatal("old connection survived reset")
	}
	if !r.Running() || c.reset.Load() != 1 {
		t.Fatal("reset stopped listener")
	}
	sock = local(t, r)
	if auth(t, sock, testPassword) != 0 || connect(t, sock, 1) != 0 {
		t.Fatal("new connection failed")
	}
}
func TestListenerValidation(t *testing.T) {
	for _, addr := range []string{"0.0.0.0:0", "127.0.0.1:99999", "example.invalid:1", "[::]:0"} {
		_, e := start("naive://u:p@proxy.invalid", addr, "nimbo", testPassword, func(context.Context, Config) (Client, error) {
			t.Fatal("invalid listener started native client")
			return nil, nil
		})
		if e == nil {
			t.Fatal("listener accepted")
		}
	}
}
