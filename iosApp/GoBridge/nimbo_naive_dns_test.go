//go:build with_naive

package main

import (
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"testing"
	"time"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf"
)

// Fast native regression for the DNS transport contract. Apple C ABI checks
// additionally execute the exact JSON emitted by the production Swift builder.
func TestNaiveDNSUsesAuthenticatedTCPProxy(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	query, _ := hex.DecodeString("123401000001000000000000076578616d706c6503636f6d0000010001")
	answer := append([]byte(nil), query...)
	answer[2] = 0x81
	answer[3] = 0x83
	served := make(chan error, 1)
	go func() {
		sock, e := listener.Accept()
		if e != nil {
			served <- e
			return
		}
		defer sock.Close()
		sock.SetDeadline(time.Now().Add(5 * time.Second))
		read := func(n int) ([]byte, error) { b := make([]byte, n); _, e := io.ReadFull(sock, b); return b, e }
		check := func(expected []byte) bool {
			b, e := read(len(expected))
			if e != nil || string(b) != string(expected) {
				served <- fmt.Errorf("unexpected SOCKS/DNS exchange")
				return false
			}
			return true
		}
		head, e := read(2)
		if e != nil || head[0] != 5 {
			served <- fmt.Errorf("SOCKS greeting")
			return
		}
		if _, e = read(int(head[1])); e != nil {
			served <- e
			return
		}
		sock.Write([]byte{5, 2})
		if !check([]byte{1, 8}) || !check([]byte("contract")) || !check([]byte{18}) || !check([]byte("local-dns-contract")) {
			return
		}
		sock.Write([]byte{1, 0})
		if !check([]byte{5, 1, 0, 1, 9, 9, 9, 9, 0, 53}) {
			return
		}
		sock.Write([]byte{5, 0, 0, 1, 127, 0, 0, 1, 0, 0})
		size, e := read(2)
		if e != nil {
			served <- e
			return
		}
		payload, e := read(int(binary.BigEndian.Uint16(size)))
		if e != nil || string(payload) != string(query) {
			served <- fmt.Errorf("DNS payload")
			return
		}
		wire := make([]byte, 2)
		binary.BigEndian.PutUint16(wire, uint16(len(answer)))
		sock.Write(append(wire, answer...))
		served <- nil
	}()
	reservation, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := reservation.LocalAddr().(*net.UDPAddr).Port
	reservation.Close()
	raw := fmt.Sprintf(`{"log":{"loglevel":"none"},"inbounds":[{"tag":"tun-in","listen":"127.0.0.1","port":%d,"protocol":"dokodemo-door","settings":{"address":"198.18.0.2","port":53,"network":"udp"}}],
 "outbounds":[{"tag":"proxy","protocol":"socks","settings":{"servers":[{"address":"127.0.0.1","port":%d,"users":[{"user":"contract","pass":"local-dns-contract"}]}]}},
 {"tag":"nimbo-naive-dns","protocol":"dns","settings":{"rewriteNetwork":"tcp","rewriteAddress":"9.9.9.9","rewritePort":53,"rules":[{"action":"direct"}]},"streamSettings":{"sockopt":{"dialerProxy":"proxy"}}}],
 "routing":{"rules":[{"type":"field","inboundTag":["tun-in"],"port":"53","network":"tcp,udp","outboundTag":"nimbo-naive-dns"}]}}`, port, listener.Addr().(*net.TCPAddr).Port)
	var configuration conf.Config
	if err = json.Unmarshal([]byte(raw), &configuration); err != nil {
		t.Fatal(err)
	}
	built, err := configuration.Build()
	if err != nil {
		t.Fatal(err)
	}
	server, err := core.New(built)
	if err != nil {
		t.Fatal(err)
	}
	defer server.Close()
	if err = server.Start(); err != nil {
		t.Fatal(err)
	}
	sock, err := net.Dial("udp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		t.Fatal(err)
	}
	defer sock.Close()
	sock.SetDeadline(time.Now().Add(6 * time.Second))
	if _, err = sock.Write(query); err != nil {
		t.Fatal(err)
	}
	response := make([]byte, 512)
	n, err := sock.Read(response)
	if err != nil {
		t.Fatal(err)
	}
	if string(response[:n]) != string(answer) {
		t.Fatal("DNS reply changed")
	}
	select {
	case err = <-served:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(time.Second):
		t.Fatal("proxy not used")
	}
}
