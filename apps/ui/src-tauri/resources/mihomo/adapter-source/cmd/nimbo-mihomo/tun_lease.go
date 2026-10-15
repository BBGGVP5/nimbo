package main

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"time"
)

// The privileged service writes one bounded frame and retains stdin. Losing
// the service/client lease cancels provider startup as well as a ready tunnel.
// Shutdown is synchronous: do not exit before routes/interface/DNS are closed.
func runTun(reader io.Reader, writer io.Writer, signals <-chan os.Signal, start, invoke func(string) string) int {
	var header [4]byte
	if _, err := io.ReadFull(reader, header[:]); err != nil {
		return 2
	}
	length := binary.BigEndian.Uint32(header[:])
	if length == 0 || length > 8<<20 {
		return 2
	}
	input := make([]byte, int(length))
	if _, err := io.ReadFull(reader, input); err != nil {
		return 2
	}
	var request struct {
		Operation string `json:"operation"`
		ID        string `json:"requestId"`
	}
	if json.Unmarshal(input, &request) != nil || request.Operation != "start" || len(request.ID) == 0 || len(request.ID) > 256 {
		return 2
	}
	leaseEnded := make(chan struct{})
	go func() { var extra [1]byte; _, _ = reader.Read(extra[:]); close(leaseEnded) }()
	result := make(chan string, 1)
	go func() { result <- start(string(input)) }()
	stop := func() int {
		cancel, _ := json.Marshal(map[string]any{"apiVersion": 1, "operation": "cancel", "targetRequestId": request.ID})
		invoke(string(cancel))
		var reply struct {
			Success bool `json:"success"`
		}
		if json.Unmarshal([]byte(invoke(`{"apiVersion":1,"operation":"stop"}`)), &reply) != nil || !reply.Success {
			return 1
		}
		return 0
	}
	select {
	case <-leaseEnded:
		stop()
		<-result
		return 1
	case <-signals:
		code := stop()
		<-result
		return code
	case reply := <-result:
		var envelope struct {
			Success bool `json:"success"`
		}
		if json.Unmarshal([]byte(reply), &envelope) != nil || !envelope.Success {
			_, _ = fmt.Fprintln(writer, reply)
			return 1
		}
		if _, err := fmt.Fprintln(writer, reply); err != nil {
			stop()
			return 1
		}
	}
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case <-leaseEnded:
			return stop()
		case <-signals:
			return stop()
		case <-ticker.C:
			var state struct {
				Data struct {
					State string `json:"state"`
				} `json:"data"`
			}
			if json.Unmarshal([]byte(invoke(`{"apiVersion":1,"operation":"status"}`)), &state) != nil || state.Data.State == "failed" {
				stop()
				return 1
			}
			if state.Data.State == "stopped" {
				return 0
			}
		}
	}
}
