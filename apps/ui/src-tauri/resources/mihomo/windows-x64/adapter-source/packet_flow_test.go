//go:build with_gvisor

package mihomocore

import (
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"net"
	"sync"
	"testing"
	"time"

	"github.com/metacubex/gvisor/pkg/buffer"
	"github.com/metacubex/gvisor/pkg/tcpip"
	"github.com/metacubex/gvisor/pkg/tcpip/header"
	"github.com/metacubex/gvisor/pkg/tcpip/stack"
)

func flowPacket(version byte) []byte {
	if version == 6 {
		raw := make([]byte, 40)
		raw[0] = 0x60
		return raw
	}
	raw := make([]byte, 20)
	raw[0] = 0x45
	binary.BigEndian.PutUint16(raw[2:4], 20)
	return raw
}

func flowDevice(t *testing.T) *PacketFlowTun {
	t.Helper()
	d, err := NewPacketFlowTun(1500)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = d.Close() })
	return d
}

func TestPacketFlowFramingAndSingleStackOwnership(t *testing.T) {
	for _, mtu := range []uint32{0, 1279, 1501, 9000} {
		if _, err := NewPacketFlowTun(mtu); err == nil {
			t.Fatal("unbounded MTU admitted")
		}
	}
	for version, protocol := range map[byte]tcpip.NetworkProtocolNumber{4: header.IPv4ProtocolNumber, 6: header.IPv6ProtocolNumber} {
		if got, err := packetFlowProtocol(flowPacket(version), 1500); err != nil || got != protocol {
			t.Fatal(got, err)
		}
	}
	bad := [][]byte{nil, {0x45}, {0x65}, {0x10}, make([]byte, 1501), append(make([]byte, 4), flowPacket(4)...)}
	for _, version := range []byte{4, 6} {
		valid := flowPacket(version)
		bad = append(bad, valid[:len(valid)-1], append(append([]byte(nil), valid...), 0))
	}
	invalidHeader := flowPacket(4)
	invalidHeader[0] = 0x4f
	bad = append(bad, invalidHeader)
	for _, raw := range bad {
		if _, err := packetFlowProtocol(raw, 1500); err == nil {
			t.Fatalf("invalid frame accepted: %x", raw)
		}
	}
	d := flowDevice(t)
	if err := d.IngestPacket(flowPacket(4)); err == nil {
		t.Fatal("unattached input accepted")
	}
	if _, _, err := d.NewEndpoint(); err != nil {
		t.Fatal(err)
	}
	if _, _, err := d.NewEndpoint(); err == nil {
		t.Fatal("two owners accepted")
	}
	_ = d.Close()
	if _, _, err := d.NewEndpoint(); !errors.Is(err, net.ErrClosed) {
		t.Fatal(err)
	}
}

type flowCapture struct {
	packet   *stack.PacketBuffer
	protocol tcpip.NetworkProtocolNumber
}

func (c *flowCapture) DeliverNetworkPacket(protocol tcpip.NetworkProtocolNumber, p *stack.PacketBuffer) {
	c.protocol = protocol
	c.packet = p.IncRef()
}
func (*flowCapture) DeliverLinkPacket(tcpip.NetworkProtocolNumber, *stack.PacketBuffer) {}

func TestPacketFlowInputIsCopied(t *testing.T) {
	d := flowDevice(t)
	c := &flowCapture{}
	d.ep.Attach(c)
	raw := flowPacket(4)
	want := append([]byte(nil), raw...)
	if err := d.IngestPacket(raw); err != nil {
		t.Fatal(err)
	}
	defer c.packet.DecRef()
	clear(raw)
	v := c.packet.ToView()
	defer v.Release()
	if c.protocol != header.IPv4ProtocolNumber || !bytes.Equal(v.AsSlice(), want) {
		t.Fatal("foreign input buffer retained")
	}
}

func TestPacketFlowOutputIsBoundedAndDrained(t *testing.T) {
	d := flowDevice(t)
	raw := flowPacket(6)
	p := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(raw)})
	defer p.DecRef()
	for n := 0; n < packetFlowQueueLimit; n++ {
		if _, err := d.WritePacket(p); err != nil {
			t.Fatal(n, err)
		}
	}
	if _, err := d.WritePacket(p); err == nil {
		t.Fatal("unbounded output queue")
	}
	out, err := d.ReadPacket(context.Background())
	if err != nil || !bytes.Equal(out, raw) {
		t.Fatal(err)
	}
	clear(out)
	next, err := d.ReadPacket(context.Background())
	if err != nil || !bytes.Equal(next, raw) {
		t.Fatal("output was not an owned copy", err)
	}
	_ = d.Close()
	if d.ep.Read() != nil {
		t.Fatal("closed device retained output")
	}
	if _, err := d.ReadPacket(context.Background()); !errors.Is(err, net.ErrClosed) {
		t.Fatal("stale output", err)
	}
	if _, err := d.WritePacket(p); !errors.Is(err, net.ErrClosed) {
		t.Fatal("late output", err)
	}
	if err := d.IngestPacket(raw); !errors.Is(err, net.ErrClosed) {
		t.Fatal("late input", err)
	}
}

func TestPacketFlowCancellationAndCloseWakeReads(t *testing.T) {
	for _, closeDevice := range []bool{false, true} {
		d := flowDevice(t)
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()
		done := make(chan error, 1)
		go func() { _, err := d.ReadPacket(ctx); done <- err }()
		if closeDevice {
			_ = d.Close()
		} else {
			cancel()
		}
		select {
		case err := <-done:
			want := error(context.Canceled)
			if closeDevice {
				want = net.ErrClosed
			}
			if !errors.Is(err, want) {
				t.Fatal(err)
			}
		case <-time.After(time.Second):
			t.Fatal("blocked packet reader leaked")
		}
	}
}

func TestPacketFlowConcurrentCloseAndOutput(t *testing.T) {
	d := flowDevice(t)
	p := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(flowPacket(4))})
	defer p.DecRef()
	var workers sync.WaitGroup
	for n := 0; n < 8; n++ {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for i := 0; i < 256; i++ {
				_, _ = d.WritePacket(p)
			}
		}()
	}
	for n := 0; n < 8; n++ {
		workers.Add(1)
		go func() { defer workers.Done(); _ = d.Close() }()
	}
	done := make(chan struct{})
	go func() { workers.Wait(); close(done) }()
	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("concurrent packet owner shutdown blocked")
	}
	if _, err := d.ReadPacket(context.Background()); !errors.Is(err, net.ErrClosed) {
		t.Fatal("output survived concurrent shutdown", err)
	}
}
