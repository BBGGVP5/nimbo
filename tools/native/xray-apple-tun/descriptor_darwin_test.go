//go:build darwin

package tun

import (
    "fmt"
    "runtime"
    "testing"
    "time"

    "golang.org/x/sys/unix"
    "github.com/xtls/xray-core/common/platform"
)

// A socketpair exercises fd ownership and blocked-reader shutdown without a
// privileged utun or external network. This is not an NE device integration test.
func TestNimboBorrowedDescriptorReconnect(t *testing.T) {
    pair, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_DGRAM, 0)
    if err != nil { t.Fatal(err) }
    defer unix.Close(pair[0])
    defer unix.Close(pair[1])
    t.Setenv(platform.TunFdKey, fmt.Sprint(pair[0]))
    for attempt := 0; attempt < 12; attempt++ {
        device, err := NewTun(&Config{MTU: 1500})
        if err != nil { t.Fatal(err) }
        tun := device.(*DarwinTun)
        if tun.tunFd == pair[0] || tun.ownsFd { t.Fatal("borrowed fd wrapped or routes claimed") }
        done := make(chan struct{})
        go func() {
            defer close(done)
            for {
                _, packet, err := tun.ReadPacket()
                if packet != nil { packet.DecRef() }
                if err == ErrQueueEmpty { tun.Wait(); continue }
                return
            }
        }()
        // Permit the reader to park in Go's poller before closing its owner.
        time.Sleep(10 * time.Millisecond)
        if err := tun.Close(); err != nil { t.Fatal(err) }
        select {
        case <-done:
        case <-time.After(time.Second): t.Fatal("old TUN reader survived close")
        }
        runtime.GC()
        if _, err := unix.FcntlInt(uintptr(pair[0]), unix.F_GETFD, 0); err != nil {
            t.Fatalf("NE descriptor closed after session %d: %v", attempt, err)
        }
        // The original remains usable, with no retired reader stealing packets.
        if _, err := unix.Write(pair[1], []byte{byte(attempt)}); err != nil { t.Fatal(err) }
        b := make([]byte, 8)
        n, err := unix.Read(pair[0], b)
        if err != nil || n != 1 || b[0] != byte(attempt) { t.Fatalf("descriptor damaged: %v %d", err, n) }
    }
}
