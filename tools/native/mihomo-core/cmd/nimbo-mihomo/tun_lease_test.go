package main

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"io"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

func leaseFrame(s string) []byte {
	b := make([]byte, 4)
	binary.BigEndian.PutUint32(b, uint32(len(s)))
	return append(b, []byte(s)...)
}

const leaseRequest = `{"apiVersion":1,"requestId":"lease-test","operation":"start","options":{"networkOwner":"desktop-tun"}}`

func TestTunLeaseRejectsUnboundedAndInvalidStartup(t *testing.T) {
	for _, b := range [][]byte{{0, 128, 0, 1}, leaseFrame(`{"operation":"stop"}`), leaseFrame(`{"operation":"start","requestId":""}`), {0, 0, 0, 2, '{'}} {
		calls := 0
		var output bytes.Buffer
		if runTun(bytes.NewReader(b), &output, make(chan os.Signal), func(string) string { calls++; return "" }, func(string) string { calls++; return "" }) != 2 || calls != 0 {
			t.Fatal("invalid frame reached native core")
		}
	}
}
func TestTunLeaseEOFJoinsCancelledStartup(t *testing.T) {
	reader, writer := io.Pipe()
	defer reader.Close()
	began := make(chan struct{})
	cancelled := make(chan struct{})
	var once sync.Once
	var output bytes.Buffer
	done := make(chan int, 1)
	var mu sync.Mutex
	ops := []string{}
	invoke := func(s string) string {
		var r struct {
			Operation string `json:"operation"`
			Target    string `json:"targetRequestId"`
		}
		_ = json.Unmarshal([]byte(s), &r)
		mu.Lock()
		ops = append(ops, r.Operation)
		mu.Unlock()
		if r.Operation == "cancel" {
			if r.Target != "lease-test" {
				t.Error("cancellation lost identity")
			}
			once.Do(func() { close(cancelled) })
		}
		return `{"success":true,"data":{"state":"stopped"}}`
	}
	go func() {
		done <- runTun(reader, &output, make(chan os.Signal), func(string) string { close(began); <-cancelled; return `{"success":false}` }, invoke)
	}()
	if _, err := writer.Write(leaseFrame(leaseRequest)); err != nil {
		t.Fatal(err)
	}
	<-began
	_ = writer.Close()
	select {
	case code := <-done:
		if code != 1 {
			t.Fatalf("code %d", code)
		}
	case <-time.After(time.Second):
		t.Fatal("lease did not cancel/join startup")
	}
	mu.Lock()
	defer mu.Unlock()
	if strings.Join(ops, ",") != "cancel,stop" {
		t.Fatalf("cleanup order %v", ops)
	}
}
func TestTunLeaseSignalStopsReadyRuntime(t *testing.T) {
	reader, writer := io.Pipe()
	defer reader.Close()
	defer writer.Close()
	signals := make(chan os.Signal, 1)
	ready := make(chan struct{})
	stopped := make(chan struct{})
	var once sync.Once
	done := make(chan int, 1)
	// Writer cannot be inspected concurrently; the readiness callback is the gate.
	go func() {
		done <- runTun(reader, io.Discard, signals, func(string) string { close(ready); return `{"success":true}` }, func(s string) string {
			if strings.Contains(s, `"operation":"stop"`) {
				once.Do(func() { close(stopped) })
			}
			return `{"success":true,"data":{"state":"running"}}`
		})
	}()
	_, _ = writer.Write(leaseFrame(leaseRequest))
	<-ready
	signals <- os.Interrupt
	select {
	case code := <-done:
		if code != 0 {
			t.Fatalf("exit %d", code)
		}
	case <-time.After(time.Second):
		t.Fatal("signal did not stop native")
	}
	select {
	case <-stopped:
	default:
		t.Fatal("process exited before cleanup")
	}
}

func TestTunLeaseCleanupFailureIsNotSuccessfulExit(t *testing.T) {
	reader, writer := io.Pipe()
	defer reader.Close()
	defer writer.Close()
	signals := make(chan os.Signal, 1)
	ready := make(chan struct{})
	done := make(chan int, 1)
	go func() {
		done <- runTun(reader, io.Discard, signals, func(string) string {
			close(ready)
			return `{"success":true}`
		}, func(s string) string {
			if strings.Contains(s, `"operation":"stop"`) {
				return `{"success":false,"error":{"code":"TUN_CLEANUP_FAILED"}}`
			}
			return `{"success":true,"data":{"state":"running"}}`
		})
	}()
	_, _ = writer.Write(leaseFrame(leaseRequest))
	<-ready
	signals <- os.Interrupt
	select {
	case code := <-done:
		if code != 1 {
			t.Fatalf("cleanup failure reported successful exit: %d", code)
		}
	case <-time.After(time.Second):
		t.Fatal("cleanup failure was not joined")
	}
}
