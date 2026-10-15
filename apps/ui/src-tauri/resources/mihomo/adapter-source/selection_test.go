package mihomocore

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"strings"
	"testing"
	"time"

	M "github.com/metacubex/sing/common/metadata"
)

func selectionEcho(t *testing.T) net.Addr {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	go func() {
		for {
			connection, err := listener.Accept()
			if err != nil {
				return
			}
			go func() { defer connection.Close(); _, _ = io.Copy(connection, connection) }()
		}
	}()
	return listener.Addr()
}

func selectionExchange(t *testing.T, connection net.Conn, reader io.Reader) {
	t.Helper()
	_ = connection.SetDeadline(time.Now().Add(2 * time.Second))
	if _, err := connection.Write([]byte("nimbo")); err != nil {
		t.Fatal(err)
	}
	data := make([]byte, 5)
	if _, err := io.ReadFull(reader, data); err != nil || string(data) != "nimbo" {
		t.Fatalf("relay failed: %q %v", data, err)
	}
}

func selectionDisconnected(t *testing.T, connection net.Conn, reader io.Reader) {
	t.Helper()
	_ = connection.SetReadDeadline(time.Now().Add(2 * time.Second))
	_, err := reader.Read(make([]byte, 1))
	if err == nil {
		t.Fatal("old selected route stayed open")
	}
	if timeout, ok := err.(net.Error); ok && timeout.Timeout() {
		t.Fatal("selection failed to terminate the old route")
	}
}

func selectionConnect(t *testing.T, proxy string, target net.Addr) (net.Conn, *bufio.Reader) {
	t.Helper()
	connection, err := net.DialTimeout("tcp", proxy, 2*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = connection.Close() })
	_ = connection.SetDeadline(time.Now().Add(2 * time.Second))
	_, err = fmt.Fprintf(connection, "CONNECT %s HTTP/1.1\r\nHost: %s\r\n\r\n", target, target)
	if err != nil {
		t.Fatal(err)
	}
	reader := bufio.NewReader(connection)
	response, err := http.ReadResponse(reader, &http.Request{Method: "CONNECT"})
	if err != nil || response.StatusCode != http.StatusOK {
		t.Fatalf("CONNECT: %v %v", response, err)
	}
	return connection, reader
}

func TestDesktopLiveSelectionClosesOnlyChangedGroup(t *testing.T) {
	stopTest(t)
	target, unrelated := selectionEcho(t), selectionEcho(t)
	config := strings.Replace(simpleConfig, "rules: [\"MATCH,Choice\"]",
		fmt.Sprintf("  - {name: Other, type: select, proxies: [local, REJECT]}\nrules: [\"DST-PORT,%d,Other\",\"MATCH,Choice\"]", unrelated.(*net.TCPAddr).Port), 1)
	_, status := startTest(t, config)
	old, oldReader := selectionConnect(t, status.MixedAddress, target)
	other, otherReader := selectionConnect(t, status.MixedAddress, unrelated)
	selectionExchange(t, old, oldReader)
	selectionExchange(t, other, otherReader)
	requireOK(t, call(t, "select", map[string]any{"group": "Choice", "name": "local"}))
	selectionExchange(t, old, oldReader)
	if call(t, "select", map[string]any{"group": "Choice", "name": "absent"}).Success {
		t.Fatal("invalid choice accepted")
	}
	selectionExchange(t, old, oldReader)
	requireOK(t, call(t, "select", map[string]any{"group": "Choice", "name": "REJECT"}))
	selectionDisconnected(t, old, oldReader)
	selectionExchange(t, other, otherReader)
	if code, _, err := proxyGet(status.MixedAddress, "http://"+target.String()); err == nil && code == 200 {
		t.Fatal("new connection escaped the selected reject route")
	}
	// A working new choice can create a fresh real relay after the old one ended.
	requireOK(t, call(t, "select", map[string]any{"group": "Choice", "name": "DIRECT"}))
	fresh, freshReader := selectionConnect(t, status.MixedAddress, target)
	selectionExchange(t, fresh, freshReader)
}

func TestMobileSelectionNeverWaitsForTCPRelayLifetime(t *testing.T) {
	m, _ := mobileFixtureSource(t, strings.Replace(strings.Replace(mobileConfig, "proxies: [{name: local, type: direct}]", "proxies: [{name: local, type: direct}, {name: alternate, type: direct}]", 1), "proxies: [local]", "proxies: [local, alternate]", 1))
	target := selectionEcho(t)
	client, inbound := net.Pipe()
	t.Cleanup(func() { _ = client.Close(); _ = inbound.Close() })
	metadata := M.Metadata{
		Source:      M.Socksaddr{Addr: netip.MustParseAddr("127.0.0.1"), Port: 1234},
		Destination: M.Socksaddr{Addr: netip.MustParseAddr("127.0.0.1"), Port: uint16(target.(*net.TCPAddr).Port)},
	}
	done := make(chan error, 1)
	go func() { done <- m.NewConnection(context.Background(), inbound, metadata) }()
	selectionExchange(t, client, client)
	// Same/invalid choices must keep the relay usable, with graph.Lock acquired
	// while TCP is live. The old implementation times out here until client ends.
	for _, name := range []string{"local", "absent"} {
		selected := make(chan testReply, 1)
		go func() {
			fields := map[string]any{"apiVersion": 1, "requestId": "switch", "operation": "select", "group": "GLOBAL", "name": name}
			data, _ := json.Marshal(fields)
			var reply testReply
			_ = json.Unmarshal([]byte(Invoke(string(data))), &reply)
			selected <- reply
		}()
		select {
		case reply := <-selected:
			if reply.Success != (name == "local") {
				t.Fatalf("unexpected choice result: %+v", reply)
			}
		case <-time.After(2 * time.Second):
			t.Fatal("live TCP blocked group selection")
		}
		selectionExchange(t, client, client)
	}
	requireOK(t, call(t, "select", map[string]any{"group": "GLOBAL", "name": "alternate"}))
	selectionDisconnected(t, client, client)
	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("relay did not finish")
	}
	fresh, freshInbound := net.Pipe()
	t.Cleanup(func() { _ = fresh.Close(); _ = freshInbound.Close() })
	go func() { _ = m.NewConnection(context.Background(), freshInbound, metadata) }()
	selectionExchange(t, fresh, fresh)
}
