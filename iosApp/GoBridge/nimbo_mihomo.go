package main

import (
	"encoding/json"
	"sync"

	mihomocore "nimbo/mihomocore"
)

const mihomoMaxRequest = 8 * 1024 * 1024

// Source-linked into upstream cgo_bridge, never a second Go runtime. The native
// wire envelope, original YAML and generation are returned without translation.
func mihomoInvokeJSON(input string) string {
	return mihomocore.Invoke(input)
}

// A separate cancellation export must never dispatch a start/select/stop by
// mistake. The native parser still validates version, duplicates and fields.
func mihomoCancelJSON(input string) string {
	var request struct {
		Operation string `json:"operation"`
	}
	if len(input) > mihomoMaxRequest || json.Unmarshal([]byte(input), &request) != nil || request.Operation != "cancel" {
		return mihomocore.Invoke("")
	}
	return mihomocore.Invoke(input)
}

func mihomoStartIOSJSON(input string, borrowedFD int64) string {
	// Native StartIOS is deliberately unavailable until Darwin FD ownership,
	// framing and readiness have been tested. Never substitute desktop-proxy.
	return mihomocore.StartIOS(input, borrowedFD)
}

// Native goroutines may retain the old interface after unregister. Drain active
// callbacks and make later calls fail closed before the host may free its context.
type mihomoProtectedCallback struct {
	mu       sync.RWMutex
	callback func(int64) bool
}

func (p *mihomoProtectedCallback) Protect(fd int64) bool {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.callback != nil && p.callback(fd)
}

func (p *mihomoProtectedCallback) deactivate() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.callback = nil
}

var mihomoProtectionRegistration struct {
	sync.Mutex
	current *mihomoProtectedCallback
}

func mihomoSetSocketProtector(callback func(int64) bool) string {
	mihomoProtectionRegistration.Lock()
	defer mihomoProtectionRegistration.Unlock()
	var next *mihomoProtectedCallback
	var native mihomocore.SocketProtector
	if callback != nil {
		next = &mihomoProtectedCallback{callback: callback}
		native = next
	}
	result := mihomocore.SetSocketProtector(native)
	var reply struct {
		Success bool `json:"success"`
	}
	if json.Unmarshal([]byte(result), &reply) == nil && reply.Success {
		if previous := mihomoProtectionRegistration.current; previous != nil {
			previous.deactivate()
		}
		mihomoProtectionRegistration.current = next
	}
	return result
}
