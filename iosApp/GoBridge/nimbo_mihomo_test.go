package main

import (
	"encoding/json"
	"strings"
	"testing"
	"time"

	libXray "github.com/xtls/libxray"
)

const mihomoBridgeFixture = "# exact source\r\nmode: rule\r\nproxies: [{name: local, type: direct}]\r\nproxy-groups: [{name: Choice, type: select, proxies: [local, DIRECT, REJECT]}]\r\nrules: ['MATCH,Choice']\r\n"

type mihomoBridgeReply struct {
	APIVersion int            `json:"apiVersion"`
	RequestID  string         `json:"requestId"`
	Success    bool           `json:"success"`
	Generation uint64         `json:"generation"`
	Data       map[string]any `json:"data"`
	Error      struct {
		Code string `json:"code"`
	} `json:"error"`
}

func bridgeReply(t *testing.T, raw string) mihomoBridgeReply {
	t.Helper()
	var result mihomoBridgeReply
	if err := json.Unmarshal([]byte(raw), &result); err != nil {
		t.Fatal(err)
	}
	if result.APIVersion != 1 {
		t.Fatal("Unexpected wire version")
	}
	return result
}

func bridgeCall(t *testing.T, operation string, fields map[string]any) mihomoBridgeReply {
	t.Helper()
	if fields == nil {
		fields = map[string]any{}
	}
	fields["apiVersion"], fields["requestId"], fields["operation"] = 1, "bridge-"+operation, operation
	input, err := json.Marshal(fields)
	if err != nil {
		t.Fatal(err)
	}
	result := bridgeReply(t, mihomoInvokeJSON(string(input)))
	if result.RequestID != "bridge-"+operation {
		t.Fatal("Lost request identity")
	}
	return result
}

func TestMihomoMergedInspectValidateAndAPI3(t *testing.T) {
	for _, operation := range []string{"inspect", "validate"} {
		r := bridgeCall(t, operation, map[string]any{"yaml": mihomoBridgeFixture})
		if !r.Success {
			t.Fatalf("%s: %s", operation, r.Error.Code)
		}
		if operation == "inspect" && r.Data["originalYAML"] != mihomoBridgeFixture {
			t.Fatal("Source changed")
		}
	}
	// Real upstream invoke in the same process catches init/protobuf collisions.
	var reply struct {
		Success bool `json:"success"`
	}
	for _, input := range []string{
		`{"apiVersion":3,"method":"xrayVersion","payload":{}}`,
		`{"apiVersion":3,"method":"testXray","payload":{"xrayJson":"{\"outbounds\":[{\"protocol\":\"freedom\"}]}"}}`,
	} {
		if err := json.Unmarshal([]byte(libXray.Invoke(input)), &reply); err != nil || !reply.Success {
			t.Fatal("Real API3 regression", err)
		}
	}
}

func TestMihomoBridgeManagedLifecycleAndCancel(t *testing.T) {
	directory := t.TempDir()
	t.Cleanup(func() { bridgeCall(t, "stop", nil); mihomoSetSocketProtector(nil) })
	if !bridgeReply(t, mihomoSetSocketProtector(func(int64) bool { return false })).Success {
		t.Fatal("Protection install failed")
	}
	retained := mihomoProtectionRegistration.current
	cancel := bridgeReply(t, mihomoCancelJSON(`{"apiVersion":1,"requestId":"cancel-first","operation":"cancel","targetRequestId":"bridge-start"}`))
	if !cancel.Success {
		t.Fatal(cancel.Error.Code)
	}
	fields := map[string]any{"yaml": mihomoBridgeFixture, "options": map[string]any{
		"dataDir": directory, "networkOwner": "desktop-proxy", "mixedAddress": "127.0.0.1:0",
		"controllerAddress": "127.0.0.1:0", "secret": strings.Repeat("bridge-test-", 4),
	}}
	cancelled := bridgeCall(t, "start", fields)
	if cancelled.Success {
		t.Fatal("Cancelled start committed")
	}
	// Fresh unique request ID; never reuse the cancelled identity.
	fields["apiVersion"], fields["requestId"], fields["operation"] = 1, "bridge-live", "start"
	input, _ := json.Marshal(fields)
	started := bridgeReply(t, mihomoInvokeJSON(string(input)))
	if !started.Success {
		t.Fatal(started.Error.Code)
	}
	if changed := bridgeReply(t, mihomoSetSocketProtector(nil)); changed.Success || changed.Error.Code != "BUSY" {
		t.Fatal("Live protection change was accepted")
	}
	if mihomoProtectionRegistration.current != retained {
		t.Fatal("Rejected registration lost the active callback")
	}
	for _, operation := range []string{"status", "snapshot", "select"} {
		r := bridgeCall(t, operation, map[string]any{"group": "Choice", "name": "REJECT", "generation": started.Generation})
		if !r.Success {
			t.Fatal(operation, r.Error.Code)
		}
	}
	if r := bridgeReply(t, mihomoCancelJSON(`{"apiVersion":1,"requestId":"wrong-cancel","operation":"stop"}`)); r.Success {
		t.Fatal("Cancel export dispatched stop")
	}
	if r := bridgeCall(t, "status", nil); r.Data["state"] != "running" {
		t.Fatal("Cancel changed live state")
	}
	if r := bridgeCall(t, "stop", map[string]any{"generation": started.Generation}); !r.Success {
		t.Fatal(r.Error.Code)
	}
}

func TestMihomoBridgeIOSGateAndMalformedInput(t *testing.T) {
	for _, input := range []string{"", strings.Repeat("x", mihomoMaxRequest+1), `{"apiVersion":9,"operation":"status"}`} {
		if bridgeReply(t, mihomoInvokeJSON(input)).Success {
			t.Fatal("Invalid request accepted")
		}
	}
	for _, fd := range []int64{0, -1, 42} {
		r := bridgeReply(t, mihomoStartIOSJSON(`{"apiVersion":1,"requestId":"ios","operation":"start"}`, fd))
		want := "PLATFORM_UNAVAILABLE"
		if fd <= 0 {
			want = "INVALID_FD"
		}
		if r.Success || r.Error.Code != want {
			t.Fatal("iOS TUN gate changed", r.Error.Code)
		}
	}
}

func TestMihomoProtectorDrainsBeforeContextRelease(t *testing.T) {
	entered, release, drained, completed := make(chan struct{}), make(chan struct{}), make(chan struct{}), make(chan bool)
	protector := &mihomoProtectedCallback{callback: func(fd int64) bool {
		close(entered)
		<-release
		return fd == 42
	}}
	go func() { completed <- protector.Protect(42) }()
	<-entered
	go func() { protector.deactivate(); close(drained) }()
	select {
	case <-drained:
		t.Fatal("Context released during active callback")
	case <-time.After(20 * time.Millisecond):
	}
	close(release)
	if !<-completed {
		t.Fatal("Active callback result changed")
	}
	<-drained
	if protector.Protect(42) {
		t.Fatal("Retained stale callback did not fail closed")
	}
}

func TestMihomoSocketRegistrationAcknowledged(t *testing.T) {
	t.Cleanup(func() { mihomoSetSocketProtector(nil) })
	if !bridgeReply(t, mihomoSetSocketProtector(func(int64) bool { return false })).Success {
		t.Fatal("Install failed")
	}
	previous := mihomoProtectionRegistration.current
	if previous == nil || previous.Protect(42) {
		t.Fatal("Deny callback lost")
	}
	if !bridgeReply(t, mihomoSetSocketProtector(nil)).Success {
		t.Fatal("Unregister failed")
	}
	if previous.Protect(42) || mihomoProtectionRegistration.current != nil {
		t.Fatal("Stale callback retained")
	}
}
