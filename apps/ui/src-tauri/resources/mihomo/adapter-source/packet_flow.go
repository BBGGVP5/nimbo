//go:build with_gvisor

package mihomocore

import (
	"context"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"sync"
	"sync/atomic"

	"github.com/metacubex/gvisor/pkg/buffer"
	"github.com/metacubex/gvisor/pkg/tcpip"
	"github.com/metacubex/gvisor/pkg/tcpip/header"
	"github.com/metacubex/gvisor/pkg/tcpip/link/channel"
	"github.com/metacubex/gvisor/pkg/tcpip/stack"
)

const packetFlowQueueLimit = 128

// PacketFlowTun is a raw-IP link, suitable for the public NEPacketTunnelFlow
// callbacks. It creates no interface, routes, FD, socket or system DNS settings.
// Each instance belongs to one stack; the owner must close it before disposing
// of callbacks. Construction alone is NOT runtime/TUN readiness.
type PacketFlowTun struct {
	ep       *channel.Endpoint
	mtu      uint32
	ingress  sync.RWMutex
	closed   atomic.Bool
	attached atomic.Bool
	once     sync.Once
}

func NewPacketFlowTun(mtu uint32) (*PacketFlowTun, error) {
	if mtu < 1280 || mtu > 1500 {
		return nil, errors.New("packet-flow MTU must be 1280..1500")
	}
	return &PacketFlowTun{ep: channel.New(packetFlowQueueLimit, mtu, ""), mtu: mtu}, nil
}

// The stack uses NewEndpoint/WritePacket, never stream-style Read/Write.
func (d *PacketFlowTun) Read([]byte) (int, error)  { return 0, io.ErrNoProgress }
func (d *PacketFlowTun) Write([]byte) (int, error) { return 0, io.ErrNoProgress }

func (d *PacketFlowTun) NewEndpoint() (stack.LinkEndpoint, stack.NICOptions, error) {
	d.ingress.RLock()
	defer d.ingress.RUnlock()
	if d.closed.Load() {
		return nil, stack.NICOptions{}, net.ErrClosed
	}
	if !d.attached.CompareAndSwap(false, true) {
		return nil, stack.NICOptions{}, errors.New("packet-flow link already owned by a stack")
	}
	return d.ep, stack.NICOptions{}, nil
}

func packetFlowProtocol(raw []byte, mtu uint32) (tcpip.NetworkProtocolNumber, error) {
	if len(raw) == 0 || len(raw) > int(mtu) {
		return 0, errors.New("invalid packet-flow size")
	}
	switch raw[0] >> 4 {
	case 4:
		if len(raw) < 20 {
			break
		}
		headerSize := int(raw[0]&15) * 4
		if headerSize < 20 || headerSize > len(raw) || int(binary.BigEndian.Uint16(raw[2:4])) != len(raw) {
			break
		}
		return header.IPv4ProtocolNumber, nil
	case 6:
		// No jumbograms: the system MTU and bounded queue are intentional.
		if len(raw) >= 40 && 40+int(binary.BigEndian.Uint16(raw[4:6])) == len(raw) {
			return header.IPv6ProtocolNumber, nil
		}
	}
	return 0, errors.New("invalid raw IPv4/IPv6 framing")
}

// IngestPacket owns a copy before passing data to asynchronous stack handlers.
// It accepts raw IP only (no Darwin AF header, utun prefix or foreign pointers).
func (d *PacketFlowTun) IngestPacket(raw []byte) error {
	protocol, err := packetFlowProtocol(raw, d.mtu)
	if err != nil {
		return err
	}
	d.ingress.RLock()
	defer d.ingress.RUnlock()
	if d.closed.Load() {
		return net.ErrClosed
	}
	if !d.ep.IsAttached() {
		return errors.New("packet-flow stack is not attached")
	}
	packet := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(append([]byte(nil), raw...))})
	defer packet.DecRef()
	d.ep.InjectInbound(protocol, packet)
	return nil
}

// Output is bounded by channel.Endpoint's nonblocking 128-packet queue.
// Never hold ingress during writes: an input can synchronously generate output.
func (d *PacketFlowTun) WritePacket(packet *stack.PacketBuffer) (int, error) {
	if d.closed.Load() {
		return 0, net.ErrClosed
	}
	if packet == nil || packet.Size() > int(d.mtu) {
		return 0, errors.New("invalid packet-flow output size")
	}
	view := packet.ToView()
	_, err := packetFlowProtocol(view.AsSlice(), d.mtu)
	view.Release()
	if err != nil {
		return 0, err
	}
	var list stack.PacketBufferList
	list.PushBack(packet)
	n, queueErr := d.ep.WritePackets(list)
	if queueErr != nil || n != 1 {
		if d.closed.Load() {
			return 0, net.ErrClosed
		}
		return 0, errors.New("packet-flow output queue is full or closed")
	}
	return packet.Size(), nil
}

// ReadPacket returns an owned raw-IP copy. Cancellation and Close wake readers;
// check closed again after dequeue. The platform owner must still check its
// generation before delivering output to an Apple callback.
func (d *PacketFlowTun) ReadPacket(ctx context.Context) ([]byte, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if d.closed.Load() {
		return nil, net.ErrClosed
	}
	packet := d.ep.ReadContext(ctx)
	if packet != nil {
		defer packet.DecRef()
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if packet == nil || d.closed.Load() {
		return nil, net.ErrClosed
	}
	view := packet.ToView()
	defer view.Release()
	return append([]byte(nil), view.AsSlice()...), nil
}

func (d *PacketFlowTun) Close() error {
	d.once.Do(func() {
		d.ingress.Lock()
		defer d.ingress.Unlock()
		d.closed.Store(true)
		d.ep.Attach(nil)
		d.ep.Close() // also drains queued references, waking blocking readers
	})
	return nil
}
