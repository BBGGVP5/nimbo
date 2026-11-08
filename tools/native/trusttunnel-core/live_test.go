package trusttunnelcore

import (
	"bufio"
	"context"
	"encoding/binary"
	"io"
	"net"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"
)

// Live check against a real endpoint (official TrustTunnel endpoint, any
// upstream). Skipped unless NIMBO_TT_TEST_LINK is set:
//
//	NIMBO_TT_TEST_LINK='tt://?...' go test -run Live -v ./...
func TestLiveEndpointHTTP2AndHTTP3(t *testing.T) {
	link := os.Getenv("NIMBO_TT_TEST_LINK")
	if link == "" {
		t.Skip("NIMBO_TT_TEST_LINK is not set")
	}
	base, err := requestFromLink(context.Background(), Request{Link: link}, hostResolver())
	if err != nil {
		t.Fatal(err)
	}
	for _, http3 := range []bool{false, true} {
		t.Run(map[bool]string{false: "http2", true: "http3"}[http3], func(t *testing.T) {
			r := base
			r.HTTP3, r.SocksUsername, r.SocksPassword, r.TimeoutMs = http3, "lu", "lp", 10000
			latency, err := Probe(r, nil)
			if err != nil {
				t.Fatalf("probe: %v", err)
			}
			t.Logf("health check %v", latency)
			rt, err := Start(r, nil)
			if err != nil {
				t.Fatalf("start: %v", err)
			}
			defer rt.Close()
			conn, err := net.Dial("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(rt.Port())))
			if err != nil {
				t.Fatal(err)
			}
			defer conn.Close()
			_ = conn.SetDeadline(time.Now().Add(20 * time.Second))
			mustWrite(t, conn, []byte{5, 1, 2})
			expect(t, conn, []byte{5, 2})
			mustWrite(t, conn, []byte{1, 2, 'l', 'u', 2, 'l', 'p'})
			expect(t, conn, []byte{1, 0})
			host := "example.com"
			req := append([]byte{5, 1, 0, 3, byte(len(host))}, host...)
			mustWrite(t, conn, binary.BigEndian.AppendUint16(req, 80))
			reply := make([]byte, 10)
			if _, err := io.ReadFull(conn, reply); err != nil || reply[1] != 0 {
				t.Fatalf("connect reply %v %v", reply, err)
			}
			mustWrite(t, conn, []byte("GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"))
			status, err := bufio.NewReader(conn).ReadString('\n')
			if err != nil || !strings.HasPrefix(status, "HTTP/1.1 ") {
				t.Fatalf("status %q %v", status, err)
			}
			t.Logf("through tunnel: %s", strings.TrimSpace(status))
		})
	}
}
