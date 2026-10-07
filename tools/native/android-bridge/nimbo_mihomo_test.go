package libXray

import (
	"encoding/json"
	"testing"
)

func TestNimboMihomoVersionedBridge(t *testing.T) {
	var result struct {
		APIVersion int    `json:"apiVersion"`
		Success    bool   `json:"success"`
		RequestID  string `json:"requestId"`
		Data       struct {
			State       string `json:"state"`
			CoreVersion string `json:"coreVersion"`
		} `json:"data"`
	}
	if err := json.Unmarshal([]byte(NimboMihomoInvoke(`{"apiVersion":1,"requestId":"bridge-status","operation":"status"}`)), &result); err != nil {
		t.Fatal(err)
	}
	if !result.Success || result.APIVersion != 1 || result.RequestID != "bridge-status" || result.Data.State != "stopped" || result.Data.CoreVersion != "v1.19.32" {
		t.Fatalf("unexpected bridge status: %+v", result)
	}
}

func TestNimboMihomoCancelCannotInjectOperation(t *testing.T) {
	var result struct {
		Success   bool   `json:"success"`
		RequestID string `json:"requestId"`
	}
	if err := json.Unmarshal([]byte(NimboMihomoCancel(`id","operation":"start`)), &result); err != nil {
		t.Fatal(err)
	}
	if !result.Success || result.RequestID != "android-cancel" {
		t.Fatalf("unexpected cancel result: %+v", result)
	}
	var status struct {
		Data struct {
			State string `json:"state"`
		} `json:"data"`
	}
	if err := json.Unmarshal([]byte(NimboMihomoInvoke(`{"apiVersion":1,"requestId":"after-cancel","operation":"status"}`)), &status); err != nil {
		t.Fatal(err)
	}
	if status.Data.State != "stopped" {
		t.Fatal("cancel changed runtime state")
	}
}
