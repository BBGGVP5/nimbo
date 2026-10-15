//go:build with_naive

package main

import (
	"context"
	"encoding/json"
	"testing"
)

func TestNaiveDiagnosticProjection(t *testing.T) {
	original := diagnosticRequest{Format: "share", Config: "naive+https://fixture:secret@192.0.2.1?peer=proxy.invalid"}
	ctx, cancel := context.WithCancel(context.Background())
	prepared, close, code := prepareNaiveDiagnostic(ctx, original)
	if code != "" || close == nil {
		t.Fatal("native diagnostic setup failed")
	}
	defer close()
	if prepared.Format != "xray" || prepared.Config == original.Config {
		t.Fatal("missing private projection")
	}
	var document map[string]any
	if json.Unmarshal([]byte(prepared.Config), &document) != nil {
		t.Fatal("invalid projection")
	}
	if _, code := diagnosticOutbounds(prepared); code != "" {
		t.Fatal("projection rejected:", code)
	}
	cancel()
	close()
	close()
	if _, cleanup, code := prepareNaiveDiagnostic(ctx, original); cleanup != nil || code != "DIAGNOSTIC_CANCELLED" {
		t.Fatal("cancelled probe started")
	}
	bad := diagnosticRequest{Format: "share", Config: "naive://secret@host"}
	if _, cleanup, code := prepareNaiveDiagnostic(context.Background(), bad); cleanup != nil || code != "DIAGNOSTIC_CONFIG" {
		t.Fatal("invalid config admitted")
	}
}
